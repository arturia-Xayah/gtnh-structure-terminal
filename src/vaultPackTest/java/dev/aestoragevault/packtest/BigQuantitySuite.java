package dev.aestoragevault.packtest;

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
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.definitions.IBlockDefinition;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEStack;
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
import dev.aestoragevault.ae.ExactStockQuery;
import dev.aestoragevault.ae.ResourceCodec;
import dev.aestoragevault.core.Resource;
import dev.aestoragevault.world.TileAccess;
import dev.aestoragevault.world.TileController;
import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;

/** Real formed vaults, two hatches on one powered AE grid, huge item/fluid counters and process restart. */
@Mod(
    modid = "bigquantitytests",
    name = "Big quantity backend validation",
    version = "1",
    dependencies = "required-after:aestoragevault")
public final class BigQuantitySuite {

    private MinecraftServer server;
    private WorldServer world;
    private FakePlayer player;
    private final TileController[] controllers = new TileController[2];
    private final TileAccess[] access = new TileAccess[2];
    private final List<Map<String, Object>> tests = new ArrayList<>();
    private int age;
    private boolean done, restart;
    private Path marker;
    private final BigInteger huge = BigInteger.ONE.shiftLeft(255)
        .add(BigInteger.valueOf(123));

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        if (Boolean.getBoolean("aestoragevault.bigQuantityTests")) {
            ForgeChunkManager.setForcedChunkLoadingCallback(this, (tickets, w) -> {});
            FMLCommonHandler.instance()
                .bus()
                .register(this);
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent e) {
        if (done || e.phase != TickEvent.Phase.END) return;
        try {
            if (++age == 1) setup();
            if (age == 60) {
                check(controllers[0].available() && controllers[1].available(), "vaults not formed");
                check(access[0].available() && access[1].available(), "AE network inactive");
                check(
                    access[0].getProxy()
                        .getGrid()
                        == access[1].getProxy()
                            .getGrid(),
                    "not one network");
                if (restart) {
                    verifyRestart();
                    finish();
                    return;
                }
                Map<Resource, BigInteger> stock = new HashMap<>();
                stock.put(item(), huge);
                stock.put(water(), huge);
                for (int i = 0; i < 2; i++) {
                    controllers[i].store()
                        .absorbExact(UUID.randomUUID(), huge, stock);
                    access[i].refresh();
                }
                row("two_real_vaults_import_huge_mixed_stock", true);
            }
            if (age == 65 && !restart) {
                nativeChecks();
                world.saveAllChunks(true, null);
                ThreadedFileIOBase.threadedIOInstance.waitForFinish();
                NBTTagCompound saved = new NBTTagCompound();
                for (int i = 0; i < 2; i++) {
                    saved.setString("Id" + i, controllers[i].id.toString());
                    saved.setByteArray(
                        "Item" + i,
                        controllers[i].store()
                            .exactAmount(item())
                            .toByteArray());
                    saved.setByteArray(
                        "Fluid" + i,
                        controllers[i].store()
                            .exactAmount(water())
                            .toByteArray());
                }
                CompressedStreamTools.writeCompressed(saved, Files.newOutputStream(marker));
                finish();
            }
            if (age > 180) throw new AssertionError("timeout");
        } catch (Throwable failure) {
            failure.printStackTrace();
            row("fatal: " + failure, false);
            try {
                finish();
            } catch (Exception e2) {
                throw new RuntimeException(e2);
            }
        }
    }

    private void setup() throws Exception {
        server = MinecraftServer.getServer();
        world = server.worldServerForDimension(0);
        check(
            server.isDedicatedServer() && world.getWorldInfo()
                .getWorldName()
                .equals("big-quantity-validation"),
            "wrong world");
        ForgeChunkManager.Ticket ticket = ForgeChunkManager.requestTicket(this, world, ForgeChunkManager.Type.NORMAL);
        ForgeChunkManager.forceChunk(ticket, new net.minecraft.world.ChunkCoordIntPair(0, 0));
        world.theChunkProviderServer.loadChunk(0, 0);
        player = new FakePlayer(
            world,
            new GameProfile(UUID.fromString("ae000000-0000-4000-8000-000000000063"), "BigQuantityTest"));
        marker = server.getFile("big-quantity-restart.nbt")
            .toPath();
        restart = Files.exists(marker);
        if (!restart) {
            for (int index = 0; index < 2; index++) {
                int z = 8 + index * 4;
                for (int x = 7; x <= 9; x++) for (int y = 79; y <= 81; y++) for (int depth = z; depth <= z + 2; depth++)
                    world.setBlock(x, y, depth, GregTechAPI.sBlockCasings1, 6, 3);
                world.setBlockToAir(8, 80, z + 1);
                world.setBlock(8, 80, z, VaultMod.CONTROLLER, 2, 3);
                world.setBlock(7, 80, z + 1, VaultMod.ACCESS, 0, 3);
                int bus = -1;
                for (int i = 0; i < GregTechAPI.METATILEENTITIES.length; i++)
                    if (GregTechAPI.METATILEENTITIES[i] != null
                        && GregTechAPI.METATILEENTITIES[i].getClass() == MTEHatchInputBus.class) {
                            bus = i;
                            break;
                        }
                world.setBlockToAir(9, 80, z + 1);
                check(
                    GregTechAPI.METATILEENTITIES[bus].getStackForm(1)
                        .tryPlaceItemIntoWorld(player, world, 9, 80, z + 1, 1, .5f, .5f, .5f),
                    "bus placement");
            }
            for (int z = 9; z <= 13; z++) place(
                AEApi.instance()
                    .definitions()
                    .blocks()
                    .controller(),
                6,
                80,
                z);
            place(
                AEApi.instance()
                    .definitions()
                    .blocks()
                    .energyCellCreative(),
                5,
                80,
                9);
        }
        for (int i = 0; i < 2; i++) {
            controllers[i] = (TileController) world.getTileEntity(8, 80, 8 + i * 4);
            controllers[i].automatic = false;
            controllers[i].checkStructure();
            access[i] = (TileAccess) world.getTileEntity(7, 80, 9 + i * 4);
        }
    }

    private void place(IBlockDefinition definition, int x, int y, int z) {
        world.setBlock(
            x,
            y,
            z,
            definition.maybeBlock()
                .get(),
            0,
            3);
    }

    private Resource item() throws Exception {
        return ResourceCodec.encode(AEItemStack.create(new ItemStack(Items.iron_ingot)));
    }

    private Resource water() throws Exception {
        return ResourceCodec.encode(AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void nativeChecks() throws Exception {
        IAEStack[] types = { AEItemStack.create(new ItemStack(Items.iron_ingot)),
            AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)) };
        for (IAEStack request : types) {
            IMEInventory monitor = access[0].getProxy()
                .getStorage()
                .getMEMonitor(request.getStackType());
            IItemList list = monitor.getAvailableItems(
                request.getStackType()
                    .createPrimitiveList(),
                appeng.util.IterationCounter.fetchNewId());
            check(
                list.findPrecise(request)
                    .getStackSize() == Long.MAX_VALUE,
                "multi-vault enumeration overflow");
            check(
                monitor.getAvailableItem(request, appeng.util.IterationCounter.fetchNewId())
                    .getStackSize() == Long.MAX_VALUE,
                "native point query overflow");
            Resource resource = ResourceCodec.encode(request);
            BigInteger before = controllers[0].store()
                .exactAmount(resource)
                .add(
                    controllers[1].store()
                        .exactAmount(resource));
            for (Actionable mode : new Actionable[] { Actionable.SIMULATE, Actionable.MODULATE }) {
                IAEStack got = monitor.extractItems(
                    request.copy()
                        .setStackSize(4096),
                    mode,
                    new MachineSource(access[0]));
                check(got != null && got.getStackSize() == 4096, "native extraction");
                BigInteger after = controllers[0].store()
                    .exactAmount(resource)
                    .add(
                        controllers[1].store()
                            .exactAmount(resource));
                check(
                    after.equals(mode == Actionable.SIMULATE ? before : before.subtract(BigInteger.valueOf(4096))),
                    "extraction not exact");
            }
            check(
                monitor.injectItems(
                    request.copy()
                        .setStackSize(4096),
                    Actionable.MODULATE,
                    new MachineSource(access[0])) == null,
                "native reinsertion");
            check(
                controllers[0].store()
                    .exactAmount(resource)
                    .add(
                        controllers[1].store()
                            .exactAmount(resource))
                    .equals(before),
                "transfer lost stock");
            row("native_" + request.getStackType() + "_huge_window_and_exact_transactions", true);
        }
        IItemList list = AEItemStackType.ITEM_STACK_TYPE.createPrimitiveList();
        list.addStorage(
            AEItemStack.create(new ItemStack(Items.iron_ingot))
                .setStackSize(Long.MAX_VALUE));
        list.addStorage(
            AEItemStack.create(new ItemStack(Items.iron_ingot))
                .setStackSize(4096));
        check(
            list.findPrecise(types[0])
                .getStackSize() == Long.MAX_VALUE,
            "item merge wrapped");
        row("native_positive_item_addition_saturates", true);
        IItemList fluids = AEFluidStackType.FLUID_STACK_TYPE.createPrimitiveList();
        fluids.addStorage(
            AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1))
                .setStackSize(Long.MAX_VALUE));
        fluids.addStorage(
            AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1))
                .setStackSize(4096));
        check(
            fluids.findPrecise(types[1])
                .getStackSize() == Long.MAX_VALUE,
            "fluid merge wrapped");
        row("native_positive_fluid_addition_saturates", true);
        final List<String> chat = new ArrayList<>();
        FakePlayer querying = new FakePlayer(world, new GameProfile(UUID.randomUUID(), "QuantityQuery")) {

            @Override
            public void addChatMessage(net.minecraft.util.IChatComponent component) {
                chat.add(net.minecraft.util.IChatComponent.Serializer.func_150696_a(component));
            }
        };
        ExactStockQuery.send(controllers[0], querying, new ItemStack(Items.iron_ingot));
        check(
            chat.size() == 1 && chat.get(0)
                .contains(
                    controllers[0].store()
                        .exactAmount(item())
                        .toString()),
            "exact query truncated");
        row("on_demand_chat_reports_full_BigInteger_quantity", true);
    }

    private void verifyRestart() throws Exception {
        NBTTagCompound saved = CompressedStreamTools.readCompressed(Files.newInputStream(marker));
        for (int i = 0; i < 2; i++) {
            check(
                controllers[i].id.toString()
                    .equals(saved.getString("Id" + i)),
                "identity changed");
            check(
                controllers[i].store()
                    .exactAmount(item())
                    .equals(new BigInteger(saved.getByteArray("Item" + i))),
                "item changed");
            check(
                controllers[i].store()
                    .exactAmount(water())
                    .equals(new BigInteger(saved.getByteArray("Fluid" + i))),
                "fluid changed");
        }
        row("fresh_process_restores_huge_item_fluid_and_UUIDs", true);
        for (IAEStack<?> request : new IAEStack[] { AEItemStack.create(new ItemStack(Items.iron_ingot)),
            AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)) }) {
            IMEInventory monitor = access[0].getProxy()
                .getStorage()
                .getMEMonitor(request.getStackType());
            IAEStack<?> result = (IAEStack<?>) monitor
                .getAvailableItem(request, appeng.util.IterationCounter.fetchNewId());
            check(result != null && result.getStackSize() == Long.MAX_VALUE, "restart visible amount");
        }
        row("fresh_process_reconnects_multi_vault_native_AE_window", true);
    }

    private void row(String name, boolean passed) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("passed", passed);
        tests.add(row);
    }

    private void finish() throws Exception {
        done = true;
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(
            "passed",
            !tests.isEmpty() && tests.stream()
                .allMatch(x -> Boolean.TRUE.equals(x.get("passed"))));
        report.put("client_launched", false);
        report.put("tests", tests);
        report.put("java", System.getProperty("java.version"));
        Files.write(
            server.getFile(restart ? "big-quantity-tests-restart.json" : "big-quantity-tests.json")
                .toPath(),
            new GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(report)
                .getBytes(StandardCharsets.UTF_8));
        server.initiateShutdown();
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
