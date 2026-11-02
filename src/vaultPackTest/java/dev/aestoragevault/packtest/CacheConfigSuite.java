package dev.aestoragevault.packtest;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.ThreadedFileIOBase;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEFluidStackType;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.aestoragevault.VaultMod;
import dev.aestoragevault.ae.NativeResourceCache;
import dev.aestoragevault.ae.ResourceCodec;
import dev.aestoragevault.config.VaultCacheConfig;
import dev.aestoragevault.core.CachePool;
import dev.aestoragevault.core.Resource;
import dev.aestoragevault.world.TileAccess;
import dev.aestoragevault.world.TileController;
import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;

/** Tiny settings, invalid-value normalization and a fresh-process restart with all caches disabled. */
@Mod(
    modid = "cacheconfigtests",
    name = "Storage cache config tests",
    version = "1",
    dependencies = "required-after:aestoragevault")
public final class CacheConfigSuite {

    private MinecraftServer server;
    private WorldServer world;
    private final TileController[] controllers = new TileController[2];
    private final TileAccess[] access = new TileAccess[2];
    private final List<Map<String, Object>> tests = new ArrayList<>();
    private final List<IAEStack<?>> stock = new ArrayList<>();
    private int age;
    private boolean restart, done;
    private Path marker;
    private final BigInteger huge = BigInteger.ONE.shiftLeft(255)
        .add(BigInteger.valueOf(71));

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (Boolean.getBoolean("aestoragevault.cacheConfigTests")) {
            ForgeChunkManager.setForcedChunkLoadingCallback(this, (tickets, w) -> {});
            FMLCommonHandler.instance()
                .bus()
                .register(this);
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;
        try {
            if (++age == 20) setup();
            if (age == 100) {
                check(access[0].available() && access[1].available(), "unformed / unpowered vault");
                check(
                    access[0].getProxy()
                        .getGrid()
                        == access[1].getProxy()
                            .getGrid(),
                    "not one grid");
                seed();
                configAndNativeChecks();
                if (!restart) {
                    world.saveAllChunks(true, null);
                    ThreadedFileIOBase.threadedIOInstance.waitForFinish();
                    Configuration cfg = new Configuration(server.getFile("config/gtnh-modernization.cfg"));
                    cfg.load();
                    for (String key : new String[] { "keyMaxEntries", "keyMaxBytes", "prototypeMaxEntries",
                        "prototypeMaxBytes", "outputMaxEntries", "outputMaxBytes", "maxEntryBytes", "globalMaxBytes" })
                        cfg.get("storagecache", key, "0")
                            .set("0");
                    cfg.save();
                    NBTTagCompound expected = new NBTTagCompound();
                    for (int n = 0; n < 2; n++) {
                        expected.setString("Id" + n, controllers[n].id.toString());
                        for (int i = 0; i < stock.size(); i++) expected.setByteArray(
                            n + "_" + i,
                            controllers[n].store()
                                .exactAmount(ResourceCodec.encode(stock.get(i)))
                                .toByteArray());
                    }
                    CompressedStreamTools.writeCompressed(expected, Files.newOutputStream(marker));
                    check(VaultCacheConfig.usedBytes() > 0, "file edit incorrectly hot-reloaded configuration");
                    row("file_edit_requires_process_restart");
                }
                finish(null);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            try {
                finish(failure);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void setup() throws Exception {
        server = MinecraftServer.getServer();
        world = server.worldServerForDimension(0);
        check(
            server.isDedicatedServer() && world.getWorldInfo()
                .getWorldName()
                .equals("cache-config-validation"),
            "wrong world");
        marker = server.getFile("cache-config-restart.nbt")
            .toPath();
        restart = Files.exists(marker);
        ForgeChunkManager.Ticket ticket = ForgeChunkManager.requestTicket(this, world, ForgeChunkManager.Type.NORMAL);
        ForgeChunkManager.forceChunk(ticket, new net.minecraft.world.ChunkCoordIntPair(0, 0));
        world.theChunkProviderServer.loadChunk(0, 0);
        FakePlayer player = new FakePlayer(
            world,
            new GameProfile(UUID.fromString("ae000000-0000-4000-8000-000000000074"), "CacheConfig"));
        int bus = -1;
        for (int i = 0; i < GregTechAPI.METATILEENTITIES.length; i++) if (GregTechAPI.METATILEENTITIES[i] != null
            && GregTechAPI.METATILEENTITIES[i].getClass() == MTEHatchInputBus.class) {
                bus = i;
                break;
            }
        if (!restart) {
            for (int n = 0; n < 2; n++) {
                int z = 8 + n * 4;
                for (int x = 7; x <= 9; x++) for (int y = 79; y <= 81; y++)
                    for (int d = z; d <= z + 2; d++) world.setBlock(x, y, d, GregTechAPI.sBlockCasings1, 6, 3);
                world.setBlockToAir(8, 80, z + 1);
                world.setBlock(8, 80, z, VaultMod.CONTROLLER, 2, 3);
                world.setBlock(7, 80, z + 1, VaultMod.ACCESS, 0, 3);
                world.setBlockToAir(9, 80, z + 1);
                check(
                    GregTechAPI.METATILEENTITIES[bus].getStackForm(1)
                        .tryPlaceItemIntoWorld(player, world, 9, 80, z + 1, 1, .5f, .5f, .5f),
                    "bus placement");
            }
            for (int z = 9; z <= 13; z++) world.setBlock(
                6,
                80,
                z,
                AEApi.instance()
                    .definitions()
                    .blocks()
                    .controller()
                    .maybeBlock()
                    .get(),
                0,
                3);
            world.setBlock(
                5,
                80,
                9,
                AEApi.instance()
                    .definitions()
                    .blocks()
                    .energyCellCreative()
                    .maybeBlock()
                    .get(),
                0,
                3);
        }
        for (int n = 0; n < 2; n++) {
            controllers[n] = (TileController) world.getTileEntity(8, 80, 8 + n * 4);
            controllers[n].automatic = false;
            controllers[n].checkStructure();
            access[n] = (TileAccess) world.getTileEntity(7, 80, 9 + n * 4);
        }
    }

    private void seed() throws Exception {
        for (int i = 0; i < 16; i++) {
            ItemStack item = new ItemStack(Items.paper);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Variant", i);
            item.setTagCompound(tag);
            stock.add(AEItemStack.create(item));
        }
        ItemStack large = new ItemStack(Items.paper);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByteArray("Large", new byte[400000]);
        large.setTagCompound(tag);
        stock.add(AEItemStack.create(large));
        stock.add(AEItemStack.create(new ItemStack(Items.iron_ingot)));
        stock.add(AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)));
        NBTTagCompound expected = restart ? CompressedStreamTools.readCompressed(Files.newInputStream(marker)) : null;
        for (int n = 0; n < 2; n++) {
            if (!restart) {
                Map<Resource, BigInteger> contents = new HashMap<>();
                for (IAEStack<?> stack : stock) contents.put(ResourceCodec.encode(stack), huge);
                controllers[n].store()
                    .absorbExact(UUID.randomUUID(), huge.multiply(BigInteger.valueOf(100)), contents);
                access[n].refresh();
            }
            check(
                controllers[n].store()
                    .types() == stock.size(),
                "restart lost types");
            if (restart) check(
                controllers[n].id.toString()
                    .equals(expected.getString("Id" + n)),
                "restart UUID changed");
            for (int i = 0; i < stock.size(); i++) check(
                controllers[n].store()
                    .exactAmount(ResourceCodec.encode(stock.get(i)))
                    .equals(restart ? new BigInteger(expected.getByteArray(n + "_" + i)) : huge),
                "restart quantity");
        }
        row(
            restart ? "fresh_process_restores_all_types_and_BigInteger_counts"
                : "two_real_vaults_seed_huge_stock_and_large_NBT");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void configAndNativeChecks() throws Exception {
        Configuration cfg = new Configuration(server.getFile("config/gtnh-modernization.cfg"));
        cfg.load();
        check(
            cfg.get("storagecache", "keyMaxEntries", "")
                .getString()
                .equals(restart ? "0" : "2"),
            "count setting not loaded");
        check(
            cfg.get("storagecache", "maxEntryBytes", "")
                .getString()
                .equals(restart ? "0" : "1048576"),
            "invalid negative limit was not normalized");
        row(restart ? "startup_loads_zero_budgets" : "startup_loads_small_limits_and_normalizes_invalid_value");
        for (int n = 0; n < 2; n++) {
            for (IAEStackType type : new IAEStackType[] { AEItemStackType.ITEM_STACK_TYPE,
                AEFluidStackType.FLUID_STACK_TYPE }) {
                IMEInventoryHandler handler = access[n].getCellArray(type)
                    .get(0);
                for (int pass = 0; pass < 3; pass++) {
                    IItemList list = handler.getAvailableItems(type.createPrimitiveList(), 0);
                    check(
                        list.size() == (type == AEItemStackType.ITEM_STACK_TYPE ? 18 : 1),
                        "budget truncated enumeration");
                    for (IAEStack<?> stack : stock) if (stack.getStackType() == type) check(
                        ((IAEStack) list.findPrecise(stack)).getStackSize() == Long.MAX_VALUE,
                        "visible quantity changed");
                    for (IAEStack<?> stack : stock) if (stack.getStackType() == type) check(
                        ((IAEStack) handler.getAvailableItem(stack, 0)).getStackSize() == Long.MAX_VALUE,
                        "point query failed");
                }
                Field f = handler.getClass()
                    .getDeclaredField("resourceCache");
                f.setAccessible(true);
                NativeResourceCache keys = (NativeResourceCache) f.get(handler);
                check(
                    keys.size() <= (restart ? 0 : 2) && keys.accountedBytes() <= (restart ? 0 : 1024),
                    "key budget exceeded");
                f = handler.getClass()
                    .getDeclaredField("prototypes");
                f.setAccessible(true);
                CachePool.Cache proto = (CachePool.Cache) f.get(handler);
                check(
                    proto.size() <= (restart ? 0 : 2) && proto.bytes() <= (restart ? 0 : 4096),
                    "prototype budget exceeded");
            }
        }
        check(VaultCacheConfig.usedBytes() <= (restart ? 0 : 8192), "global budget exceeded");
        row(
            restart ? "disabled_caches_retain_nothing_but_return_every_type"
                : "per_channel_count_bytes_single_entry_and_global_caps_hold");
        NativeResourceCache isolated = new NativeResourceCache();
        isolated.encode(stock.get(16));
        Field f = NativeResourceCache.class.getDeclaredField("probe");
        f.setAccessible(true);
        Object probe = f.get(isolated);
        Field tag = probe.getClass()
            .getDeclaredField("tag");
        tag.setAccessible(true);
        check(tag.get(probe) == null, "probe retained rejected huge NBT");
        row("rejected_or_disabled_key_probe_retains_no_NBT");
        CachePool.Cache<String, String> output = VaultCacheConfig.outputCache();
        output.put("a", "A", 800);
        output.put("b", "B", 800);
        check(output.size() == (restart ? 0 : 1), "output count setting ignored");
        output.clear();
        row("output_cache_limits_and_global_accounting");
        for (IAEStack<?> request : stock) {
            IMEInventory monitor = access[0].getProxy()
                .getStorage()
                .getMEMonitor(request.getStackType());
            BigInteger before = controllers[0].store()
                .exactAmount(ResourceCodec.encode(request))
                .add(
                    controllers[1].store()
                        .exactAmount(ResourceCodec.encode(request)));
            IAEStack got = (IAEStack) monitor.extractItems(
                request.copy()
                    .setStackSize(64),
                Actionable.SIMULATE,
                new MachineSource(access[0]));
            check(got != null && got.getStackSize() == 64, "simulate");
            got = (IAEStack) monitor.extractItems(
                request.copy()
                    .setStackSize(64),
                Actionable.MODULATE,
                new MachineSource(access[0]));
            check(got != null && got.getStackSize() == 64, "extract");
            check(
                monitor.injectItems(
                    request.copy()
                        .setStackSize(64),
                    Actionable.MODULATE,
                    new MachineSource(access[0])) == null,
                "insert");
            BigInteger after = controllers[0].store()
                .exactAmount(ResourceCodec.encode(request))
                .add(
                    controllers[1].store()
                        .exactAmount(ResourceCodec.encode(request)));
            check(before.equals(after), "native transfers lost stock");
        }
        row("native_AE_simulate_extract_insert_conservation_with_limits");
    }

    private void row(String name) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("name", name);
        r.put("passed", true);
        tests.add(r);
    }

    private void finish(Throwable failure) throws Exception {
        done = true;
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("passed", failure == null && tests.size() == (restart ? 6 : 7));
        report.put("client_launched", false);
        report.put("tests", tests);
        report.put("java", System.getProperty("java.version"));
        if (failure != null) report.put("failure", failure.toString());
        Files.write(
            server.getFile(restart ? "cache-config-tests-restart.json" : "cache-config-tests.json")
                .toPath(),
            new GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(report)
                .getBytes(StandardCharsets.UTF_8));
        server.initiateShutdown();
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
