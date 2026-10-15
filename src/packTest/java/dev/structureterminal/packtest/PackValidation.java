package dev.structureterminal.packtest;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.structure.ISpecialItemBlock;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.structureterminal.StructureTerminal;
import dev.structureterminal.TerminalConfig;
import dev.structureterminal.TerminalSettings;
import dev.structureterminal.bridge.ItemKeys;
import dev.structureterminal.bridge.NativeBridge;
import dev.structureterminal.bridge.StructureScanner;
import dev.structureterminal.bridge.StructureScanner.Cell;
import dev.structureterminal.bridge.StructureScanner.Scan;
import dev.structureterminal.server.BuildService;
import dev.structureterminal.server.TerminalResultEvent;
import io.netty.channel.embedded.EmbeddedChannel;

/** Test-only mod: run against the official pack; never included in a normal terminal artifact. */
@Mod(
    modid = "structureterminalpacktest",
    name = "GTNH machine validation",
    version = "1",
    dependencies = "required-after:gregtech;required-after:structureterminal")
public final class PackValidation {

    private final Gson gson = new GsonBuilder().setPrettyPrinting()
        .create();
    private final Gson compact = new Gson();
    private MinecraftServer server;
    private WorldServer world;
    private FakePlayer player;
    private Object registry;
    private final List<Integer> ids = new ArrayList<Integer>();
    private final List<ForgeChunkManager.Ticket> tickets = new ArrayList<ForgeChunkManager.Ticket>();
    private final Set<String> touched = new LinkedHashSet<String>();
    private final List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
    private final Map<String, ItemStack> pool = new LinkedHashMap<String, ItemStack>();
    private final Map<String, Long> injected = new LinkedHashMap<String, Long>();
    private Map<String, Long> before;
    private Map<String, Object> row, phases;
    private NativeBridge bridge;
    private Scan scan;
    private TerminalSettings settings;
    private Path output;
    private int index, age, stage, ticks;
    private int currentId;
    private String outcome = "", detail = "";
    private final List<String> messages = new ArrayList<String>();
    private boolean initialized, finished, buildComplete;
    private static final int X = 512, Y = 128, Z = 512;
    private static final String[] STAGES = { "baseline_build", "partial_rebuild", "replace", "demolish" };

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (!Boolean.getBoolean("structureterminal.packTests") || !FMLCommonHandler.instance()
            .getSide()
            .isServer()) throw new IllegalStateException("Pack validation requires an explicit dedicated test server");
        ForgeChunkManager.setForcedChunkLoadingCallback(
            this,
            (old, loaded) -> { for (ForgeChunkManager.Ticket t : old) ForgeChunkManager.releaseTicket(t); });
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @SubscribeEvent
    public void result(TerminalResultEvent event) {
        if (event.player == player) {
            outcome = event.key;
            detail = event.value;
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        try {
            if (!initialized) {
                initialize();
                initialized = true;
            }
            ticks++;
            if (row == null) {
                if (index >= ids.size()) {
                    finish();
                    return;
                }
                begin(ids.get(index++));
                return;
            }
            if (stage < 0) {
                completeRow();
                return;
            }
            if (++age % 6 == 0) refill();
            if (age < 12) return;
            boolean active = busy();
            if (active && age < 240) return;
            if (active) {
                cancel();
                outcome = "TIMEOUT_240_TICKS";
            }
            endPhase();
        } catch (Throwable error) {
            if (error instanceof VirtualMachineError) throw (VirtualMachineError) error;
            try {
                if (row != null) {
                    row.put("harness_error", error.toString());
                    completeRow();
                } else {
                    error.printStackTrace();
                    finish();
                }
            } catch (Exception nested) {
                nested.printStackTrace();
                server.initiateShutdown();
                finished = true;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void initialize() throws Exception {
        server = MinecraftServer.getServer();
        world = server.worldServerForDimension(0);
        if (!server.isDedicatedServer() || !world.getWorldInfo()
            .getWorldName()
            .equals("terminal-pack-validation")) throw new IllegalStateException("Wrong server/world");
        output = server.getFile("machine-validation")
            .toPath();
        Files.createDirectories(output);
        boolean resume = Boolean.getBoolean("structureterminal.resumePackTests");
        if (!resume) Files.deleteIfExists(output.resolve("machines.jsonl"));
        List<String> mods = new ArrayList<String>();
        for (ModContainer mod : Loader.instance()
            .getModList()) mods.add(mod.getModId() + "=" + mod.getVersion());
        Files.write(output.resolve("loaded-mods.txt"), mods, StandardCharsets.UTF_8);
        registry = Class.forName("gregtech.api.GregTechAPI")
            .getField("METATILEENTITIES")
            .get(null);
        List<String> all = new ArrayList<String>();
        all.add("meta_id,class,constructable,multiblock");
        for (int i = 1; i < Array.getLength(registry); i++) {
            Object m = Array.get(registry, i);
            if (m == null) continue;
            boolean multi = m instanceof IConstructable;
            for (Class<?> c = m.getClass(); c != null; c = c.getSuperclass()) if (c.getSimpleName()
                .contains("MultiBlock")
                || c.getSimpleName()
                    .contains("Multiblock"))
                multi = true;
            all.add(
                i + ","
                    + m.getClass()
                        .getName()
                    + ","
                    + (m instanceof IConstructable)
                    + ","
                    + multi);
            if (multi) ids.add(i);
        }
        String filter = System.getProperty("structureterminal.machineIds", "");
        if (!filter.isEmpty()) {
            Set<Integer> selected = new LinkedHashSet<Integer>();
            for (String id : filter.split(",")) selected.add(Integer.parseInt(id));
            ids.removeIf(id -> !selected.contains(id));
        }
        if (resume && Files.isRegularFile(output.resolve("machines.jsonl"))) {
            for (String line : Files.readAllLines(output.resolve("machines.jsonl"), StandardCharsets.UTF_8)) {
                Map<String, Object> completed = compact
                    .fromJson(line, new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType());
                int completedId = ((Number) completed.get("meta_id")).intValue();
                if (index >= ids.size() || ids.get(index) != completedId)
                    throw new IllegalStateException("Resume list does not match controller registry");
                results.add(completed);
                index++;
            }
        }
        Files.write(output.resolve("all-gregtech-machines.csv"), all, StandardCharsets.UTF_8);
        world.setSpawnLocation(X, Y, Z);
        world.getGameRules()
            .setOrCreateGameRule("doMobSpawning", "false");
        world.getGameRules()
            .setOrCreateGameRule("doFireTick", "false");
        for (int cx = (X >> 4) - 8; cx <= (X >> 4) + 8; cx++) for (int cz = (Z >> 4) - 8; cz <= (Z >> 4) + 8; cz++) {
            if (tickets.isEmpty() || tickets.get(tickets.size() - 1)
                .getChunkList()
                .size()
                >= tickets.get(tickets.size() - 1)
                    .getChunkListDepth()) {
                ForgeChunkManager.Ticket t = ForgeChunkManager
                    .requestTicket(this, world, ForgeChunkManager.Type.NORMAL);
                if (t == null) throw new IllegalStateException("No test chunk ticket");
                tickets.add(t);
            }
            ForgeChunkManager.forceChunk(tickets.get(tickets.size() - 1), new ChunkCoordIntPair(cx, cz));
            world.theChunkProviderServer.loadChunk(cx, cz);
        }
        player = new FakePlayer(
            world,
            new GameProfile(UUID.fromString("ae000000-0000-4000-8000-000000000002"), "GTNHValidation")) {

            @Override
            public void addChatComponentMessage(net.minecraft.util.IChatComponent text) {
                messages.add(text.getUnformattedText());
            }
        };
        NetworkManager wire = new NetworkManager(false);
        new EmbeddedChannel(wire);
        new NetHandlerPlayServer(server, wire, player) {

            @Override
            public void sendPacket(Packet packet) {}
        };
        player.setPosition(X + 3.5, Y, Z + 3.5);
        player.capabilities.allowEdit = true;
        player.capabilities.isCreativeMode = false;
        server.getConfigurationManager().playerEntityList.add(player);
        world.playerEntities.add(player);
        player.inventory.setInventorySlotContents(0, new ItemStack(StructureTerminal.item));
        player.inventory.currentItem = 0;
        TerminalConfig.budget = 200;
        TerminalConfig.scanMillis = 5000;
        TerminalConfig.cells = 100000;
        TerminalConfig.rounds = 2000;
        TerminalConfig.demolishTiles = false;
        System.out.println("PACK_VALIDATION_START machines=" + ids.size());
        writeProgress();
    }

    private void begin(int id) throws Exception {
        cleanup();
        currentId = id;
        row = new LinkedHashMap<String, Object>();
        phases = new LinkedHashMap<String, Object>();
        row.put("meta_id", id);
        row.put(
            "class",
            Array.get(registry, id)
                .getClass()
                .getName());
        row.put("phases", phases);
        stage = -1;
        Object prototype = Array.get(registry, id);
        try {
            row.put(
                "name",
                prototype.getClass()
                    .getMethod("getLocalName")
                    .invoke(prototype));
        } catch (Exception ignored) {}
        if (!(prototype instanceof IConstructable)) {
            row.put("scan_status", "NO_STRUCTURELIB_INTERFACE");
            return;
        }
        placeController();
        settings = new TerminalSettings();
        boolean explicitHatch = Boolean.getBoolean("structureterminal.explicitHatchOnly");
        if (!explicitHatch) settings.channels.put("gt_hatch", 1);
        row.put("native_hatch_channel_explicit", !explicitHatch);
        row.put("energy_tier_override", Integer.getInteger("structureterminal.energyTier." + currentId, -1));
        if (bridge.alignment != null) settings.mirror = bridge.alignment.getFlip()
            .isHorizontallyFlipped();
        long start = System.nanoTime();
        try {
            scan = StructureScanner.scan(bridge, player, settings);
        } catch (Throwable failure) {
            row.put("scan_status", "FAILED");
            row.put("scan_error", failure.toString());
            return;
        }
        row.put("scan_ms", (System.nanoTime() - start) / 1000000L);
        row.put("scan_status", "PASS");
        row.put("cells", scan.cells.size());
        row.put("unknown_cells", scan.unknown);
        row.put("channels", new ArrayList<String>(scan.channels.keySet()));
        for (Cell c : scan.cells) touched.add(c.position());
        if (explicitHatch) {
            Cell chosen = null;
            ItemStack part = null;
            int score = Integer.MAX_VALUE;
            for (Cell cell : scan.cells) for (ItemStack candidate : cell.candidates) {
                int candidateId = candidate.getItemDamage();
                if (candidateId <= 0 || candidateId >= Array.getLength(registry)) continue;
                Object m = Array.get(registry, candidateId);
                Block b = Block.getBlockFromItem(candidate.getItem());
                if (m == null || b == null || !b.hasTileEntity(candidateId) || !energyEligible(candidate)) continue;
                int candidateScore = cell.candidates.size();
                if (m.getClass()
                    .getSimpleName()
                    .contains("Muffler")) candidateScore -= 10000;
                if (candidateScore < score) {
                    score = candidateScore;
                    chosen = cell;
                    part = candidate;
                }
            }
            if (chosen != null) {
                settings.choices.put(chosen.selectionKey, ItemKeys.key(part));
                row.put("explicit_hatch_choice", ItemKeys.key(part));
            }
        }
        Map<String, Object> state = state();
        StructureScanner.scan(bridge, player, settings);
        row.put("idle_scan_state_preserved", state.equals(state()));
        if (!bridge.supportsSurvivalConstruction()) {
            row.put("build_status", "NO_SURVIVAL_INTERFACE");
            return;
        }
        stage = 0;
        startPhase(1, false);
        writeProgress();
    }

    private void placeController() throws Exception {
        Object prototype = Array.get(registry, currentId);
        ItemStack controller = (ItemStack) prototype.getClass()
            .getMethod("getStackForm", long.class)
            .invoke(prototype, 1L);
        world.setBlockToAir(X, Y, Z);
        if (!controller.copy()
            .tryPlaceItemIntoWorld(player, world, X, Y, Z, 1, 0.5f, 0.5f, 0.5f))
            throw new IllegalStateException("Controller placement failed");
        bridge = NativeBridge.resolve(world, X, Y, Z, 2);
        if (bridge == null) throw new IllegalStateException("Placed controller has no constructable bridge");
        try {
            bridge.tile.getClass()
                .getMethod("disableWorking")
                .invoke(bridge.tile);
        } catch (ReflectiveOperationException ignored) {}
        touched.add(X + "," + Y + "," + Z);
    }

    private void startPhase(int action, boolean confirmed) throws Exception {
        preparePool();
        before = materialState();
        injected.clear();
        outcome = "";
        detail = "";
        messages.clear();
        age = 0;
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("x", X);
        tag.setInteger("y", Y);
        tag.setInteger("z", Z);
        tag.setInteger("side", 2);
        tag.setInteger("action", action);
        tag.setBoolean("confirmed", confirmed);
        tag.setTag("settings", settings.write());
        BuildService.INSTANCE.enqueue(player, tag);
    }

    private void endPhase() throws Exception {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("outcome", outcome);
        result.put("detail", detail);
        result.put("ticks", age);
        result.put("messages", new ArrayList<String>(messages));
        List<String> installed = new ArrayList<String>();
        for (Cell cell : scan.cells) {
            TileEntity part = world.getTileEntity(cell.x, cell.y, cell.z);
            if (part != null) {
                try {
                    Object m = part.getClass()
                        .getMethod("getMetaTileEntity")
                        .invoke(part);
                    if (m != null) installed.add(
                        cell.position() + " "
                            + m.getClass()
                                .getName()
                            + " "
                            + worldStack(cell));
                } catch (Exception ignored) {
                    installed.add(
                        cell.position() + " "
                            + part.getClass()
                                .getName());
                }
            }
        }
        result.put("installed_tile_entities", installed);
        result.put("non_air_cells", nonAir());
        Map<String, Long> delta = materialState();
        for (Map.Entry<String, Long> e : before.entrySet()) add(delta, e.getKey(), -e.getValue());
        for (Map.Entry<String, Long> e : injected.entrySet()) add(delta, e.getKey(), -e.getValue());
        delta.entrySet()
            .removeIf(e -> e.getValue() == 0L);
        result.put("material_delta", delta);
        result.put("material_accounting_balanced", delta.isEmpty());
        result.put("controller_preserved", world.getTileEntity(X, Y, Z) == bridge.tile);
        phases.put(STAGES[stage], result);
        if (stage == 0) {
            buildComplete = outcome.endsWith(".complete");
            row.put("build_status", buildComplete ? "NATIVE_COMPLETE" : "NOT_COMPLETE");
            try {
                Object formed = forceCheck();
                row.put("formed_after_build", formed);
                row.put("structure_errors", describe(field(bridge.context, "structureErrors"), 0));
            } catch (Exception failure) {
                row.put("formed_check_error", failure.toString());
            }
            if (buildComplete) {
                int holes = 0;
                for (Cell c : scan.cells) if (!c.air && world.getTileEntity(c.x, c.y, c.z) == null
                    && !world.isAirBlock(c.x, c.y, c.z)
                    && holes++ % 3 == 0) world.setBlockToAir(c.x, c.y, c.z);
                row.put("partial_removed_cells", (holes + 2) / 3);
                placeController();
                stage = 1;
                startPhase(1, false);
                return;
            }
            stage = 3;
            startPhase(3, true);
            return;
        }
        if (stage == 1) {
            try {
                result.put("formed_after_operation", forceCheck());
            } catch (Exception error) {
                result.put("formed_check_error", error.toString());
            }
            boolean selected = false;
            for (String channel : scan.channels.keySet()) {
                if (!channel.toLowerCase(java.util.Locale.ROOT)
                    .contains("coil")
                    && !channel.toLowerCase(java.util.Locale.ROOT)
                        .contains("glass"))
                    continue;
                List<Integer> values = scan.channelValues.get(channel);
                for (int value : values) if (value > 1) {
                    settings.channels.put(channel, value);
                    row.put("replacement_channel", channel);
                    row.put("replacement_value", value);
                    selected = true;
                    break;
                }
                if (selected) break;
            }
            if (selected) {
                try {
                    scan = StructureScanner.scan(bridge, player, settings);
                    for (Cell c : scan.cells) touched.add(c.position());
                    stage = 2;
                    startPhase(4, true);
                    return;
                } catch (Exception failure) {
                    row.put("replacement_scan_error", failure.toString());
                }
            }
            row.put("replace_status", selected ? "SCAN_FAILED" : "NO_COIL_OR_GLASS_CHANNEL");
            stage = 3;
            startPhase(3, true);
            return;
        }
        if (stage == 2) {
            try {
                result.put("formed_after_operation", forceCheck());
            } catch (Exception error) {
                result.put("formed_check_error", error.toString());
            }
            row.put("replace_status", outcome.endsWith(".complete") ? "NATIVE_COMPLETE" : "NOT_COMPLETE");
            stage = 3;
            startPhase(3, true);
            return;
        }
        row.put(
            "demolish_status",
            outcome.endsWith(".demolished") ? "FINISHED_WITH_TILE_ENTITIES_SKIPPED" : "NOT_COMPLETE");
        row.put("remaining_ordinary_blocks", ordinary());
        stage = -1;
    }

    private Object forceCheck() throws Exception {
        for (Method m : bridge.context.getClass()
            .getMethods())
            if (m.getName()
                .equals("checkStructure") && m.getParameterTypes().length == 2
                && m.getParameterTypes()[0] == boolean.class
                && m.getParameterTypes()[1].isInstance(bridge.tile)) return m.invoke(bridge.context, true, bridge.tile);
        return "NO_CHECK_STRUCTURE_API";
    }

    private void preparePool() {
        pool.clear();
        for (int i = 1; i < 36; i++) player.inventory.setInventorySlotContents(i, null);
        if (stage == 3) return;
        // Prefer ordinary casing candidates, then supply common hatches and each group's first native option.
        for (Cell c : scan.groups.values()) for (ItemStack s : c.candidates) {
            Block b = Block.getBlockFromItem(s.getItem());
            if (b != null && !b.hasTileEntity(s.getItemDamage())) {
                pool.putIfAbsent(ItemKeys.key(s), s.copy());
                break;
            }
        }
        for (Cell c : scan.groups.values()) {
            for (String type : new String[] { "InputBus", "OutputBus", "HatchInput", "HatchOutput", "HatchEnergy",
                "Maintenance", "Muffler" }) for (ItemStack s : c.candidates) {
                    int id = s.getItemDamage();
                    if (id > 0 && id < Array.getLength(registry)) {
                        Object m = Array.get(registry, id);
                        if (m != null && m.getClass()
                            .getSimpleName()
                            .contains(type)) {
                            int tier = Integer.getInteger("structureterminal.energyTier." + currentId, -1);
                            if (type.equals("HatchEnergy") && tier >= 0) {
                                Object actual = field(m, "mTier");
                                if (!(actual instanceof Number) || ((Number) actual).intValue() != tier) continue;
                            }
                            pool.putIfAbsent(ItemKeys.key(s), s.copy());
                            break;
                        }
                    }
                }
            for (ItemStack s : c.candidates) if (energyEligible(s)) {
                pool.putIfAbsent(ItemKeys.key(s), s.copy());
                break;
            }
        }
        row.put("stock_types", pool.size());
        row.put("inventory_type_limit_exceeded", pool.size() > 35);
        refill();
    }

    private boolean energyEligible(ItemStack stack) {
        int tier = Integer.getInteger("structureterminal.energyTier." + currentId, -1);
        if (tier < 0) return true;
        int id = stack.getItemDamage();
        if (id <= 0 || id >= Array.getLength(registry)) return true;
        Object m = Array.get(registry, id);
        if (m == null || !m.getClass()
            .getSimpleName()
            .contains("HatchEnergy")) return true;
        Object actual = field(m, "mTier");
        return actual instanceof Number && ((Number) actual).intValue() == tier;
    }

    private void refill() {
        int slot = 1;
        for (ItemStack type : pool.values()) {
            if (slot >= 36) break;
            int max = Math.max(1, Math.min(64, type.getMaxStackSize()));
            ItemStack old = player.inventory.getStackInSlot(slot);
            if (old != null && !ItemKeys.same(old, type)) {
                slot++;
                continue;
            }
            int have = old != null && ItemKeys.same(old, type) ? old.stackSize : 0;
            if (have < max) {
                ItemStack copy = type.copy();
                copy.stackSize = max;
                player.inventory.setInventorySlotContents(slot, copy);
                add(injected, ItemKeys.key(type), (long) max - have);
            }
            slot++;
        }
    }

    private Map<String, Object> state() {
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        for (String name : new String[] { "mMachine", "mProgresstime", "mMaxProgresstime", "mEUt" })
            r.put(name, String.valueOf(field(bridge.context, name)));
        r.put(
            "facing",
            bridge.alignment == null ? "none"
                : bridge.alignment.getExtendedFacing()
                    .toString());
        return r;
    }

    private Object field(Object target, String name) {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException ignored) {}
        return "NOT_EXPOSED";
    }

    private Object describe(Object value, int depth) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean)
            return value;
        if (value instanceof Enum) return value.toString();
        if (depth > 5) return value.getClass()
            .getName();
        if (value instanceof Iterable) {
            List<Object> list = new ArrayList<Object>();
            for (Object child : (Iterable<?>) value) {
                if (list.size() >= 64) break;
                list.add(describe(child, depth + 1));
            }
            return list;
        }
        if (value.getClass()
            .isArray()) {
            List<Object> list = new ArrayList<Object>();
            for (int i = 0; i < Math.min(Array.getLength(value), 64); i++)
                list.add(describe(Array.get(value, i), depth + 1));
            return list;
        }
        if (value.getClass()
            .getName()
            .startsWith("gregtech.api.structure.error")) {
            Map<String, Object> object = new LinkedHashMap<String, Object>();
            object.put(
                "type",
                value.getClass()
                    .getSimpleName());
            for (Class<?> type = value.getClass(); type != null && type != Object.class; type = type.getSuperclass())
                for (Field f : type.getDeclaredFields())
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) try {
                        f.setAccessible(true);
                        object.put(f.getName(), describe(f.get(value), depth + 1));
                    } catch (Exception ignored) {}
            return object;
        }
        return value.getClass()
            .getName();
    }

    private Map<String, Long> materialState() {
        Map<String, Long> r = new LinkedHashMap<String, Long>();
        for (int i = 1; i < 36; i++) {
            ItemStack s = player.inventory.getStackInSlot(i);
            if (s != null) add(r, ItemKeys.key(s), (long) s.stackSize);
        }
        for (Cell c : scan.cells) {
            if (c.air || world.isAirBlock(c.x, c.y, c.z)) continue;
            ItemStack s = worldStack(c);
            if (s != null) add(r, ItemKeys.key(s), 1L);
        }
        for (Object e : world.loadedEntityList) if (e instanceof EntityItem && !((EntityItem) e).isDead) {
            ItemStack s = ((EntityItem) e).getEntityItem();
            add(r, ItemKeys.key(s), (long) s.stackSize);
        }
        return r;
    }

    private ItemStack worldStack(Cell c) {
        try {
            TileEntity tile = world.getTileEntity(c.x, c.y, c.z);
            if (tile != null) {
                try {
                    Object m = tile.getClass()
                        .getMethod("getMetaTileEntity")
                        .invoke(tile);
                    if (m != null) return (ItemStack) m.getClass()
                        .getMethod("getStackForm", long.class)
                        .invoke(m, 1L);
                } catch (NoSuchMethodException ordinaryTileEntity) {
                    // Non-GT tile entities still have a normal block item and belong in the ledger.
                }
            }
            Block b = world.getBlock(c.x, c.y, c.z);
            Item item = Item.getItemFromBlock(b);
            if (item == null) return null;
            int meta = world.getBlockMetadata(c.x, c.y, c.z);
            int damage = item instanceof ISpecialItemBlock
                ? ((ISpecialItemBlock) item).getItemMetaFromBlockMeta(b, meta)
                : b.getDamageValue(world, c.x, c.y, c.z);
            return new ItemStack(item, 1, damage);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void add(Map<String, Long> map, String key, long count) {
        Long old = map.get(key);
        map.put(key, (old == null ? 0L : old) + count);
    }

    private int nonAir() {
        int n = 0;
        for (Cell c : scan.cells) if (!c.air && !world.isAirBlock(c.x, c.y, c.z)) n++;
        return n;
    }

    private int ordinary() {
        int n = 0;
        for (Cell c : scan.cells)
            if (!c.air && !world.isAirBlock(c.x, c.y, c.z) && world.getTileEntity(c.x, c.y, c.z) == null) n++;
        return n;
    }

    private boolean busy() throws Exception {
        Field f = BuildService.class.getDeclaredField("jobs");
        f.setAccessible(true);
        return ((Map<?, ?>) f.get(BuildService.INSTANCE)).containsKey(player.getUniqueID());
    }

    private void cancel() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("action", 2);
        BuildService.INSTANCE.enqueue(player, tag);
    }

    private void cleanup() throws Exception {
        if (player != null) {
            Field f = BuildService.class.getDeclaredField("jobs");
            f.setAccessible(true);
            ((Map<?, ?>) f.get(BuildService.INSTANCE)).clear();
        }
        for (String position : touched) {
            String[] p = position.split(",");
            world.setBlockToAir(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        }
        touched.clear();
        for (Object e : new ArrayList<Object>(world.loadedEntityList))
            if (e instanceof EntityItem) ((EntityItem) e).setDead();
    }

    private void completeRow() throws Exception {
        cleanup();
        results.add(row);
        Files.write(
            output.resolve("machines.jsonl"),
            (compact.toJson(row) + "\n").getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
        System.out.println(
            "PACK_MACHINE " + currentId
                + " "
                + index
                + "/"
                + ids.size()
                + " "
                + row.get("scan_status")
                + " "
                + row.get("build_status"));
        row = null;
        writeProgress();
    }

    private void writeProgress() throws Exception {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("total_multiblocks", ids.size());
        p.put("completed", results.size());
        p.put("current_meta_id", currentId);
        p.put("finished", finished);
        Files.write(
            output.resolve("progress.json"),
            gson.toJson(p)
                .getBytes(StandardCharsets.UTF_8));
    }

    private void finish() throws Exception {
        finished = true;
        if (server == null) return;
        cleanup();
        Map<String, Object> report = new LinkedHashMap<String, Object>();
        report.put("pack", "2.9.0-RC-2");
        report.put("full_pack", true);
        report.put("all_requested_machines_attempted", results.size() == ids.size());
        report.put("scope", "GT registry multiblocks; default size/mode plus one exposed coil/glass variation");
        report.put("running_recipe_tested", false);
        report.put("gui_tested", false);
        report.put("machines", results);
        Files.write(
            output.resolve("report.json"),
            gson.toJson(report)
                .getBytes(StandardCharsets.UTF_8));
        writeProgress();
        world.playerEntities.remove(player);
        server.getConfigurationManager().playerEntityList.remove(player);
        for (ForgeChunkManager.Ticket t : tickets) ForgeChunkManager.releaseTicket(t);
        server.initiateShutdown();
    }
}
