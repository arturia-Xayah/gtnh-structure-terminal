package dev.structureterminal.client.update;

import java.io.File;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import org.apache.logging.log4j.LogManager;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.structureterminal.Tags;
import dev.structureterminal.update.ReleaseCatalog;
import dev.structureterminal.update.ServerVersionProbe;
import dev.structureterminal.update.UpdateInstaller;
import dev.structureterminal.update.UpdateTransport;

public final class UpdateClient {

    public static final UpdateClient INSTANCE = new UpdateClient();
    private static final int BUTTON_ID = 209731;
    private final ConcurrentLinkedQueue<Runnable> main = new ConcurrentLinkedQueue<Runnable>();
    private final ExecutorService worker = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "Modernization updater");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger generation = new AtomicInteger();
    private final AtomicBoolean cancel = new AtomicBoolean();
    private Configuration config;
    public boolean enabled, previews, serverAutomatic;
    private String skipped, override;
    public String pack;
    public String state = "idle";
    public String detail = "";
    public String lastInstallMessage = "";
    public String serverVersion;
    public ReleaseCatalog.Release release;
    public volatile long downloaded;
    private boolean startupChecked;
    private Path transaction;
    private Path source;
    // Intentionally kept open until OS process exit, not released from an early shutdown hook.
    private FileChannel sessionChannel;
    private FileLock sessionLock;
    private Connection connection;

    private static final class Connection {

        String version;
        boolean shown;
    }

    private UpdateClient() {}

    public void init() {
        config = new Configuration(
            new File(Minecraft.getMinecraft().mcDataDir, "config/gtnh-modernization-updates.cfg"));
        config.load();
        enabled = config.getBoolean(
            "checkAtStartup",
            "client",
            true,
            "Check once on the main menu, asynchronously. Failures never block play.");
        previews = config.getBoolean(
            "includePrereleases",
            "client",
            Tags.VERSION.contains("-"),
            "Include alpha/beta/RC releases for this GTNH version.");
        serverAutomatic = config.getBoolean(
            "autoDownloadServerVersion",
            "client",
            false,
            "Automatically download the exact newer server version. Installation still requires Exit and install.");
        skipped = config.getString(
            "skippedVersion",
            "client",
            "",
            "A skipped menu update does not suppress server-required updates.");
        override = config.getString(
            "gtnhVersionOverride",
            "client",
            "",
            "Optional exact pack version if mainmenu:version.txt is unavailable. Blank means detect; unknown versions are never assumed compatible.");
        config.save();
        readLastInstall();
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    private void readLastInstall() {
        final Path cache = Minecraft.getMinecraft().mcDataDir.toPath()
            .resolve(".modernization-updates");
        worker.execute(() -> {
            try {
                Path pointer = cache.resolve("last-install.properties");
                if (!Files.isRegularFile(pointer) || Files.size(pointer) > 16384) return;
                Properties saved = UpdateInstaller.load(pointer);
                Path tx = cache.resolve(saved.getProperty("transaction"))
                    .toRealPath();
                if (!tx.getParent()
                    .equals(cache.toRealPath())) return;
                Path resultFile = tx.resolve("result.properties");
                if (!Files.isRegularFile(resultFile) || Files.size(resultFile) > 16384) return;
                Properties result = UpdateInstaller.load(resultFile);
                String state = result.getProperty("status", "failed");
                String value = "installed".equals(state) ? result.getProperty("version", "?") : tx.toString();
                main.add(
                    () -> lastInstallMessage = tr(
                        "installed".equals(state) ? "previousSuccess" : "previousFailure",
                        value));
            } catch (Exception error) {
                log(error);
            }
        });
    }

    public static String tr(String key, Object... args) {
        return StatCollector.translateToLocalFormatted("modernization.update." + key, args);
    }

    public static boolean isMenu(GuiScreen screen) {
        if (screen instanceof GuiMainMenu) return true;
        if (screen == null || !screen.getClass()
            .getName()
            .equals("lumien.custommainmenu.gui.GuiCustom")) return false;
        try {
            Object value = screen.getClass()
                .getField("guiConfig")
                .get(screen);
            return "mainmenu".equals(
                value.getClass()
                    .getField("name")
                    .get(value));
        } catch (ReflectiveOperationException error) {
            return false;
        }
    }

    private void detectPack() {
        if (pack != null) return;
        if (!override.isEmpty()) {
            String parsed = ReleaseCatalog.packVersion(override);
            if (override.equals(parsed)) pack = parsed;
            return;
        }
        try (InputStream in = Minecraft.getMinecraft()
            .getResourceManager()
            .getResource(new ResourceLocation("mainmenu", "version.txt"))
            .getInputStream()) {
            byte[] bytes = new byte[4097];
            int total = 0, n;
            while (total < bytes.length && (n = in.read(bytes, total, bytes.length - total)) > 0) total += n;
            if (total <= 4096) pack = ReleaseCatalog.packVersion(new String(bytes, 0, total, StandardCharsets.UTF_8));
        } catch (Exception ignored) { /* Unknown pack: do not offer automatic installation. */ }
    }

    @SubscribeEvent
    public void menu(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!isMenu(event.gui)) return;
        detectPack();
        int width = 136, x = Math.max(2, event.gui.width - width - 6), y = 6;
        // Avoid the existing GTNH/CMM controls instead of replacing or reordering them.
        for (; y + 20 < event.gui.height - 4; y += 22) {
            boolean occupied = false;
            for (Object item : event.buttonList) {
                GuiButton b = (GuiButton) item;
                if (b.id == BUTTON_ID) return;
                if (b.visible && x < b.xPosition + b.width
                    && x + width > b.xPosition
                    && y < b.yPosition + b.height
                    && y + 20 > b.yPosition) occupied = true;
            }
            if (!occupied) break;
        }
        event.buttonList.add(new UpdateButton(BUTTON_ID, x, Math.min(y, event.gui.height - 24), width, tr("entry")));
        if (!startupChecked) {
            startupChecked = true;
            if (enabled) check(null);
        }
    }

    @SubscribeEvent
    public void action(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.button.id == BUTTON_ID && isMenu(event.gui)) {
            event.setCanceled(true);
            Minecraft.getMinecraft()
                .displayGuiScreen(new UpdateScreen(event.gui));
        }
    }

    @SubscribeEvent
    public void connecting(GuiOpenEvent event) {
        // ClientConnectedToServerEvent is too late: FML fires it only AFTER successful mod negotiation.
        if (!(event.gui instanceof GuiConnecting)) return;
        ServerData data = Minecraft.getMinecraft()
            .func_147104_D();
        if (data == null) return;
        final String serverAddress = data.serverIP;
        final Connection attempt = new Connection();
        connection = attempt;
        worker.execute(() -> {
            try {
                // ServerAddress handles Minecraft SRV lookup; keep DNS work off the render thread too.
                ServerAddress parsed = ServerAddress.func_78860_a(serverAddress);
                InetSocketAddress address = new InetSocketAddress(parsed.getIP(), parsed.getPort());
                String version = ServerVersionProbe.query(address, parsed.getIP());
                main.add(() -> {
                    if (connection == attempt && version != null && ReleaseCatalog.compare(version, Tags.VERSION) > 0)
                        attempt.version = version;
                });
            } catch (Exception error) {
                log(error);
            }
        });
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Runnable task;
        while ((task = main.poll()) != null) task.run();
        Minecraft mc = Minecraft.getMinecraft();
        if (connection != null && !connection.shown
            && connection.version != null
            && mc.theWorld == null
            && mc.currentScreen instanceof GuiDisconnected
            && !busy()) {
            connection.shown = true;
            check(connection.version);
            mc.displayGuiScreen(new UpdateScreen(mc.currentScreen));
        }
    }

    public boolean busy() {
        return state.equals("checking") || state.equals("downloading") || state.equals("installing");
    }

    public void check(String exact) {
        if (busy()) return;
        if (state.equals("ready")) {
            if (exact == null) return;
            if (release != null && exact.equals(release.version)) {
                serverVersion = exact;
                return;
            }
            transaction = null;
            source = null;
        }
        detectPack();
        serverVersion = exact;
        release = null;
        detail = "";
        if (pack == null) {
            state = "unknownPack";
            return;
        }
        state = "checking";
        final int token = generation.incrementAndGet();
        final boolean include = previews;
        final String targetPack = pack;
        worker.execute(() -> {
            try {
                String url = exact == null ? ReleaseCatalog.API + "?per_page=100"
                    : ReleaseCatalog.API + "/tags/v" + exact + "-GTNH-" + targetPack;
                ReleaseCatalog.Release found = ReleaseCatalog
                    .select(UpdateTransport.metadata(url), Tags.VERSION, targetPack, include, exact);
                main.add(() -> {
                    if (generation.get() != token) return;
                    release = found;
                    state = found == null ? (exact == null ? "current" : "unavailable") : "available";
                    if (found != null && exact == null && found.version.equals(skipped)) state = "skipped";
                    if (found != null && exact != null && serverAutomatic) download();
                });
            } catch (Exception error) {
                fail(token, error);
            }
        });
    }

    public void download() {
        if (release == null || busy() || state.equals("ready")) return;
        state = "downloading";
        detail = "";
        downloaded = 0;
        cancel.set(false);
        final ReleaseCatalog.Release selected = release;
        final int token = generation.incrementAndGet();
        worker.execute(() -> {
            Path tx = null;
            try {
                Path installed = UpdateInstaller.installedJar(
                    Loader.instance()
                        .getIndexedModList()
                        .get("structureterminal")
                        .getSource()
                        .toPath());
                UpdateInstaller.rejectDuplicates(installed);
                Path cache = Minecraft.getMinecraft().mcDataDir.toPath()
                    .resolve(".modernization-updates");
                Files.createDirectories(cache);
                tx = Files.createTempDirectory(cache, "update-");
                UpdateTransport.download(selected, tx.resolve("download.jar"), cancel, n -> downloaded = n);
                Properties plan = new Properties();
                plan.setProperty("source", installed.toString());
                plan.setProperty("oldSha256", UpdateInstaller.sha256(installed));
                plan.setProperty("newSha256", selected.sha256);
                plan.setProperty("version", selected.version);
                UpdateInstaller.save(tx.resolve("plan.properties"), plan);
                final Path completed = tx;
                main.add(() -> {
                    if (generation.get() != token) return;
                    if (cancel.get()) {
                        state = "available";
                        return;
                    }
                    transaction = completed;
                    source = installed;
                    state = "ready";
                });
            } catch (Exception error) {
                if (cancel.get()) main.add(() -> { if (generation.get() == token) state = "available"; });
                else fail(token, error);
            }
        });
    }

    public void cancelDownload() {
        cancel.set(true);
    }

    public void installAndExit() {
        if (!state.equals("ready") || transaction == null) return;
        state = "installing";
        final int token = generation.incrementAndGet();
        worker.execute(() -> {
            Process helper = null;
            try {
                Path tx = transaction.toRealPath();
                // Recheck after the player may have spent time reading notes.
                UpdateTransport.verifyJar(tx.resolve("download.jar"), release.version, pack);
                UpdateInstaller.rejectDuplicates(source);
                Files.copy(source, tx.resolve("installer.jar"), StandardCopyOption.REPLACE_EXISTING);
                sessionChannel = FileChannel
                    .open(tx.resolve("session.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                sessionLock = sessionChannel.lock();
                String executable = System.getProperty("os.name", "")
                    .toLowerCase(java.util.Locale.ROOT)
                    .contains("win") ? "java.exe" : "java";
                File javaExecutable = new File(new File(System.getProperty("java.home"), "bin"), executable);
                Properties pointer = new Properties();
                pointer.setProperty(
                    "transaction",
                    tx.getFileName()
                        .toString());
                UpdateInstaller.save(
                    tx.getParent()
                        .resolve("last-install.properties"),
                    pointer);
                helper = new ProcessBuilder(
                    javaExecutable.getAbsolutePath(),
                    "-cp",
                    tx.resolve("installer.jar")
                        .toString(),
                    UpdateInstaller.class.getName(),
                    tx.toString()).redirectErrorStream(true)
                        .redirectOutput(
                            tx.resolve("installer.log")
                                .toFile())
                        .start();
                long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
                while (!Files.exists(tx.resolve("ready")) && helper.isAlive() && System.nanoTime() < until)
                    Thread.sleep(50);
                if (!Files.exists(tx.resolve("ready")) || !helper.isAlive())
                    throw new java.io.IOException("Updater could not start; game left running");
                main.add(
                    () -> Minecraft.getMinecraft()
                        .shutdown());
            } catch (Exception error) {
                if (helper != null) {
                    helper.destroyForcibly();
                    try {
                        helper.waitFor();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread()
                            .interrupt();
                    }
                }
                try {
                    if (sessionLock != null) sessionLock.release();
                    if (sessionChannel != null) sessionChannel.close();
                } catch (java.io.IOException ignored) {}
                fail(token, error);
            }
        });
    }

    public void skip() {
        if (release != null && serverVersion == null && !busy() && !state.equals("ready")) {
            skipped = release.version;
            state = "skipped";
            save();
        }
    }

    public void save() {
        config.get("client", "checkAtStartup", true)
            .set(enabled);
        config.get("client", "includePrereleases", Tags.VERSION.contains("-"))
            .set(previews);
        config.get("client", "autoDownloadServerVersion", false)
            .set(serverAutomatic);
        config.get("client", "skippedVersion", "")
            .set(skipped);
        config.save();
    }

    private void fail(int token, Exception error) {
        log(error);
        main.add(() -> {
            if (generation.get() == token) {
                state = "failed";
                detail = error.getMessage() == null ? error.getClass()
                    .getSimpleName() : error.getMessage();
            }
        });
    }

    private static void log(Exception error) {
        LogManager.getLogger("ModernizationUpdater")
            .info("Optional update operation: {}", error.toString());
    }
}
