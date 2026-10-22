package dev.aestoragevault.packtest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
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
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.me.helpers.IGridProxyable;
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
import dev.aestoragevault.ae.CellReader;
import dev.aestoragevault.ae.ResourceCodec;
import dev.aestoragevault.core.Resource;
import dev.aestoragevault.core.VaultStore;
import dev.aestoragevault.ui.VaultContainer;
import dev.aestoragevault.world.TileAccess;
import dev.aestoragevault.world.TileController;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import io.netty.channel.embedded.EmbeddedChannel;

/** Real pack-only fixture. Never included in the installable mod. */
@Mod(
    modid = "vaulttests",
    name = "Storage Vault backend validation",
    version = "1",
    dependencies = "required-after:aestoragevault;required-after:ae2fc")
public final class VaultSuite {

    private static final int X = 8, Y = 80, Z = 8;
    private MinecraftServer server;
    private WorldServer world;
    private FakePlayer player;
    private TileController controller;
    private TileAccess access;
    private MTEHatchInputBus bus;
    private final List<Map<String, Object>> rows = new ArrayList<>();
    private int age;
    private boolean done, restart;
    private ItemStack restoredSource;
    private long expectedCapacity;
    private Path marker;

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        if (!Boolean.getBoolean("aestoragevault.tests")) return;
        if (!FMLCommonHandler.instance()
            .getSide()
            .isServer()) return;
        ForgeChunkManager.setForcedChunkLoadingCallback(this, (tickets, w) -> {});
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || done) return;
        try {
            age++;
            if (age == 1) setup();
            if (age == 80) {
                if (restart) {
                    restartTests();
                    finish();
                    return;
                }
                initialTests();
                // Restore a source after its durable external transaction (the world-rollback crash window).
                restoredSource = AEApi.instance()
                    .definitions()
                    .materials()
                    .cell1kPart()
                    .maybeStack(1)
                    .get();
                NBTTagCompound tag = new NBTTagCompound();
                UUID receipt = UUID.randomUUID();
                tag.setString(TileController.TOKEN, receipt.toString());
                restoredSource.setTagCompound(tag);
                controller.store()
                    .absorb(receipt, 1024, Collections.emptyMap());
                expectedCapacity = controller.store()
                    .capacity();
                bus.setInventorySlotContents(5, restoredSource.copy());
                bus.markDirty();
            }
            if (age == 120) {
                run("restored_source_consumed_without_duplicate_credit", () -> {
                    check(!hasItem(restoredSource), "restored source remains");
                    check(
                        controller.store()
                            .capacity() == expectedCapacity,
                        "capacity credited twice");
                });
                afterImportTests();
                controller.automatic = false;
                controller.markDirty();
                NBTTagCompound saved = new NBTTagCompound();
                saved.setString("Vault", controller.id.toString());
                saved.setLong(
                    "Bytes",
                    controller.store()
                        .capacity());
                saved.setLong(
                    "Iron",
                    controller.store()
                        .amount(ResourceCodec.encode(AEItemStack.create(new ItemStack(Items.iron_ingot)))));
                CompressedStreamTools.writeCompressed(saved, Files.newOutputStream(marker));
                world.saveAllChunks(true, null);
                ThreadedFileIOBase.threadedIOInstance.waitForFinish();
                finish();
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            record("harness", false, failure.toString());
            try {
                finish();
            } catch (Exception nested) {
                nested.printStackTrace();
                if (server != null) server.initiateShutdown();
            }
        }
    }

    private void setup() throws Exception {
        server = MinecraftServer.getServer();
        world = server.worldServerForDimension(0);
        check(
            server.isDedicatedServer() && world.getWorldInfo()
                .getWorldName()
                .equals("vault-validation"),
            "not disposable validation world");
        ForgeChunkManager.Ticket ticket = ForgeChunkManager.requestTicket(this, world, ForgeChunkManager.Type.NORMAL);
        check(ticket != null, "chunk ticket");
        ForgeChunkManager.forceChunk(ticket, new net.minecraft.world.ChunkCoordIntPair(0, 0));
        marker = server.getFile("vault-restart.nbt")
            .toPath();
        restart = Files.exists(marker);
        for (int cx = -1; cx <= 2; cx++)
            for (int cz = -1; cz <= 2; cz++) world.theChunkProviderServer.loadChunk(cx, cz);
        world.getGameRules()
            .setOrCreateGameRule("doMobSpawning", "false");
        player = new FakePlayer(
            world,
            new GameProfile(UUID.fromString("ae000000-0000-4000-8000-000000000061"), "VaultTest"));
        NetworkManager wire = new NetworkManager(false);
        new EmbeddedChannel(wire);
        new NetHandlerPlayServer(server, wire, player) {

            @Override
            public void sendPacket(Packet p) {}
        };
        player.setPosition(X + .5, Y, Z - 1.5);
        player.capabilities.allowEdit = true;
        server.getConfigurationManager().playerEntityList.add(player);
        world.playerEntities.add(player);
        if (!restart) {
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int depth = 0; depth <= 2; depth++)
                world.setBlock(X + dx, Y + dy, Z + depth, GregTechAPI.sBlockCasings1, 6, 3);
            world.setBlockToAir(X, Y, Z + 1);
            world.setBlock(X, Y, Z, VaultMod.CONTROLLER, 2, 3);
            world.setBlock(X - 1, Y, Z + 1, VaultMod.ACCESS, 0, 3);
            int id = -1;
            for (int n = 0; n < GregTechAPI.METATILEENTITIES.length; n++) if (GregTechAPI.METATILEENTITIES[n] != null
                && GregTechAPI.METATILEENTITIES[n].getClass() == MTEHatchInputBus.class
                && ((MTEHatchInputBus) GregTechAPI.METATILEENTITIES[n]).mTier == 4) {
                    id = n;
                    break;
                }
            check(id >= 0, "no native input bus");
            world.setBlockToAir(X + 1, Y, Z);
            check(
                GregTechAPI.METATILEENTITIES[id].getStackForm(1)
                    .tryPlaceItemIntoWorld(player, world, X + 1, Y, Z, 1, .5f, .5f, .5f),
                "bus placement");
            place(
                AEApi.instance()
                    .definitions()
                    .blocks()
                    .controller(),
                X - 2,
                Y,
                Z + 1);
            place(
                AEApi.instance()
                    .definitions()
                    .blocks()
                    .energyCellCreative(),
                X - 3,
                Y,
                Z + 1);
        }
        controller = (TileController) world.getTileEntity(X, Y, Z);
        access = (TileAccess) world.getTileEntity(X - 1, Y, Z + 1);
        bus = (MTEHatchInputBus) ((IGregTechTileEntity) world.getTileEntity(X + 1, Y, Z)).getMetaTileEntity();
        check(controller != null && access != null && bus != null, "world tiles missing");
        if (!restart) {
            bus.setInventorySlotContents(
                0,
                AEApi.instance()
                    .definitions()
                    .materials()
                    .cell1kPart()
                    .maybeStack(2)
                    .get());
            ItemStack filled = AEApi.instance()
                .definitions()
                .items()
                .cell4k()
                .maybeStack(1)
                .get();
            IMEInventoryHandler<IAEItemStack> cell = cell(filled, AEItemStackType.ITEM_STACK_TYPE);
            check(
                cell.injectItems(
                    AEItemStack.create(new ItemStack(Items.iron_ingot))
                        .setStackSize(12345),
                    Actionable.MODULATE,
                    new MachineSource(access)) == null,
                "fill item cell");
            ((ICellInventoryHandler<?>) cell).getCellInv()
                .getUpgradesInventory()
                .setInventorySlotContents(
                    0,
                    AEApi.instance()
                        .definitions()
                        .materials()
                        .cardFuzzy()
                        .maybeStack(1)
                        .get());
            check(CellReader.read(filled).contents.size() == 2, "nonempty card read");
            bus.setInventorySlotContents(1, filled);
            Object fluidItem = Class.forName("com.glodblock.github.loader.ItemAndBlockHolder")
                .getField("CELL1K")
                .get(null);
            ItemStack fluid = new ItemStack((Item) fluidItem);
            check(
                cell(fluid, AEFluidStackType.FLUID_STACK_TYPE).injectItems(
                    AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 900000)),
                    Actionable.MODULATE,
                    new MachineSource(access)) == null,
                "fill fluid cell");
            check(CellReader.read(fluid).contents.size() == 1, "fluid cell read");
            bus.setInventorySlotContents(2, fluid);
            bus.setInventorySlotContents(3, new ItemStack(Items.apple));
            bus.setInventorySlotContents(
                4,
                AEApi.instance()
                    .definitions()
                    .items()
                    .cellCreative()
                    .maybeStack(1)
                    .get());
            bus.markDirty();
        }
    }

    private void flexibleShellTests() throws Exception {
        final int cx = 24, cy = 110, cz = 24;
        for (int face = 0; face < 6; face++) {
            final int facing = face;
            run("all_shell_positions_six_faces_" + face, () -> {
                List<int[]> positions = new ArrayList<>();
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) if (dev.aestoragevault.core.VaultGeometry.shell(dx, dy, dz)
                        && !(dx == dev.aestoragevault.core.VaultGeometry.normal(facing, 0)
                            && dy == dev.aestoragevault.core.VaultGeometry.normal(facing, 1)
                            && dz == dev.aestoragevault.core.VaultGeometry.normal(facing, 2)))
                        positions.add(new int[] { cx + dx, cy + dy, cz + dz });
                check(positions.size() == 25, "shell count");
                for (int i = 0; i < positions.size(); i++) for (int swap = 0; swap < 2; swap++) {
                    fillCube(cx, cy, cz);
                    int[] a = positions.get(i), b = positions.get((i + 1) % positions.size());
                    int px = cx + dev.aestoragevault.core.VaultGeometry.normal(facing, 0);
                    int py = cy + dev.aestoragevault.core.VaultGeometry.normal(facing, 1);
                    int pz = cz + dev.aestoragevault.core.VaultGeometry.normal(facing, 2);
                    world.setBlock(px, py, pz, VaultMod.CONTROLLER, facing, 2);
                    placeTestBus(swap == 0 ? a : b);
                    int[] port = swap == 0 ? b : a;
                    world.setBlock(port[0], port[1], port[2], VaultMod.ACCESS, 0, 2);
                    TileController sample = (TileController) world.getTileEntity(px, py, pz);
                    sample.checkStructure();
                    check(sample.formed, "rejected face=" + facing + " position=" + i + " role=" + swap);
                    check(
                        dev.aestoragevault.world.VaultStructure.skinTile(world, port[0], port[1], port[2], facing) >= 0,
                        "port not skinned");
                }
                clearCube(cx, cy, cz);
            });
        }
        run("controller_off_center_and_inward_facing_rejected", () -> {
            fillCube(cx, cy, cz);
            world.setBlock(cx - 1, cy - 1, cz - 1, VaultMod.CONTROLLER, 2, 2);
            placeTestBus(new int[] { cx + 1, cy, cz });
            world.setBlock(cx - 1, cy, cz, VaultMod.ACCESS, 0, 2);
            TileController sample = (TileController) world.getTileEntity(cx - 1, cy - 1, cz - 1);
            sample.checkStructure();
            check(!sample.formed, "corner controller accepted");
            clearCube(cx, cy, cz);
        });
        for (int shared = 0; shared < 2; shared++) {
            final boolean sharedBus = shared == 0;
            run(sharedBus ? "shared_bus_rejects_both_and_recovers" : "shared_access_rejects_both_and_recovers", () -> {
                fillCube(cx, cy, cz);
                fillCube(cx + 2, cy, cz);
                world.setBlock(cx - 1, cy, cz, VaultMod.CONTROLLER, 4, 2);
                world.setBlock(cx + 3, cy, cz, VaultMod.CONTROLLER, 5, 2);
                if (sharedBus) {
                    placeTestBus(new int[] { cx + 1, cy, cz });
                    world.setBlock(cx - 1, cy + 1, cz, VaultMod.ACCESS, 0, 2);
                    world.setBlock(cx + 3, cy + 1, cz, VaultMod.ACCESS, 0, 2);
                } else {
                    world.setBlock(cx + 1, cy, cz, VaultMod.ACCESS, 0, 2);
                    placeTestBus(new int[] { cx - 1, cy + 1, cz });
                    placeTestBus(new int[] { cx + 3, cy + 1, cz });
                }
                TileController a = (TileController) world.getTileEntity(cx - 1, cy, cz);
                TileController b = (TileController) world.getTileEntity(cx + 3, cy, cz);
                check(
                    dev.aestoragevault.world.VaultStructure.inspect(a) != null
                        && dev.aestoragevault.world.VaultStructure.inspect(b) != null,
                    "invalid overlap fixture");
                a.checkStructure();
                b.checkStructure();
                check(!a.formed && !b.formed, "shared component accepted");
                b.checkStructure();
                a.checkStructure();
                check(!a.formed && !b.formed, "order-dependent ownership");
                world.setBlock(cx + 3, cy, cz, GregTechAPI.sBlockCasings1, 6, 2);
                a.checkStructure();
                check(a.formed, "ownership did not recover");
                clearCube(cx, cy, cz);
                clearCube(cx + 2, cy, cz);
            });
        }
        run("native_machine_bus_reservation_and_release", () -> {
            fillCube(cx, cy, cz);
            world.setBlock(cx, cy, cz - 1, VaultMod.CONTROLLER, 2, 2);
            placeTestBus(new int[] { cx + 1, cy, cz });
            world.setBlock(cx - 1, cy, cz, VaultMod.ACCESS, 0, 2);
            TileController sample = (TileController) world.getTileEntity(cx, cy, cz - 1);
            sample.checkStructure();
            check(sample.formed, "native test baseline");
            int[] nativePos = { cx + 5, cy, cz };
            world.setBlockToAir(nativePos[0], nativePos[1], nativePos[2]);
            check(
                GregTechAPI.METATILEENTITIES[141].getStackForm(1)
                    .tryPlaceItemIntoWorld(player, world, nativePos[0], nativePos[1], nativePos[2], 1, .5f, .5f, .5f),
                "native controller placement");
            gregtech.api.metatileentity.implementations.MTEMultiBlockBase machine = (gregtech.api.metatileentity.implementations.MTEMultiBlockBase) ((IGregTechTileEntity) world
                .getTileEntity(nativePos[0], nativePos[1], nativePos[2])).getMetaTileEntity();
            MTEHatchInputBus sampleBus = (MTEHatchInputBus) ((IGregTechTileEntity) world.getTileEntity(cx + 1, cy, cz))
                .getMetaTileEntity();
            machine.mMachine = true;
            machine.mInputBusses.add(sampleBus);
            dev.aestoragevault.world.NativeBusOwners.checked(machine);
            sample.checkStructure();
            check(!sample.formed, "native bus sharing accepted");
            machine.mInputBusses.clear();
            sample.checkStructure();
            check(sample.formed, "native bus release not detected");
            world.setBlockToAir(nativePos[0], nativePos[1], nativePos[2]);
            clearCube(cx, cy, cz);
        });
    }

    private void fillCube(int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
            for (int dz = -1; dz <= 1; dz++) world.setBlock(x + dx, y + dy, z + dz, GregTechAPI.sBlockCasings1, 6, 2);
        world.setBlockToAir(x, y, z);
    }

    private void clearCube(int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
            for (int dz = -1; dz <= 1; dz++) world.setBlockToAir(x + dx, y + dy, z + dz);
    }

    private void placeTestBus(int[] pos) {
        world.setBlockToAir(pos[0], pos[1], pos[2]);
        check(
            GregTechAPI.METATILEENTITIES[bus.getBaseMetaTileEntity()
                .getMetaTileID()].getStackForm(1)
                    .tryPlaceItemIntoWorld(player, world, pos[0], pos[1], pos[2], 1, .5f, .5f, .5f),
            "sample bus placement");
    }

    private void initialTests() throws Exception {
        run("native_3x3x3_structure_and_AE_channel", () -> {
            check(controller.formed, "not formed: " + controller.status);
            check(
                access.available(),
                "AE access inactive " + access.getProxy()
                    .isActive() + " " + controller.status);
        });
        run("component_stack_and_nonempty_cells_absorbed", () -> {
            check(controller.store() != null, controller.status);
            check(
                controller.store()
                    .capacity() == 7168,
                "capacity=" + controller.store()
                    .capacity() + " status=" + controller.status);
            check(
                !hasItem(
                    AEApi.instance()
                        .definitions()
                        .materials()
                        .cell1kPart()
                        .maybeStack(1)
                        .get()),
                "component retained");
            check(
                !hasItem(
                    AEApi.instance()
                        .definitions()
                        .items()
                        .cell4k()
                        .maybeStack(1)
                        .get()),
                "filled cell retained");
        });
        run(
            "real_item_cell_contents_migrated",
            () -> check(
                controller.store()
                    .amount(ResourceCodec.encode(AEItemStack.create(new ItemStack(Items.iron_ingot)))) == 12345,
                "iron lost"));
        run(
            "real_fluid_cell_contents_migrated",
            () -> check(
                controller.store()
                    .amount(ResourceCodec.encode(AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1))))
                    == 900000,
                "water lost"));
        run(
            "cell_upgrade_card_preserved_as_item",
            () -> check(
                controller.store()
                    .amount(
                        ResourceCodec.encode(
                            AEItemStack.create(
                                AEApi.instance()
                                    .definitions()
                                    .materials()
                                    .cardFuzzy()
                                    .maybeStack(1)
                                    .get())))
                    == 1,
                "upgrade lost"));
        run(
            "unsupported_and_creative_cells_retained",
            () -> check(
                hasItem(new ItemStack(Items.apple)) && hasItem(
                    AEApi.instance()
                        .definitions()
                        .items()
                        .cellCreative()
                        .maybeStack(1)
                        .get()),
                "unsupported source consumed"));
        run("AE_grid_reads_migrated_contents", () -> {
            IAEItemStack iron = storage().getItemInventory()
                .extractItems(
                    AEItemStack.create(new ItemStack(Items.iron_ingot))
                        .setStackSize(10),
                    Actionable.SIMULATE,
                    new MachineSource(access));
            check(iron != null && iron.getStackSize() == 10, "grid missing inventory");
        });
    }

    private void afterImportTests() throws Exception {
        flexibleShellTests();
        run("controller_and_access_require_LuV_assembler_recipes", () -> {
            int count = 0;
            for (gregtech.api.util.GTRecipe recipe : gregtech.api.recipe.RecipeMaps.assemblerRecipes.getAllRecipes()) {
                if (recipe.mOutputs.length != 1) continue;
                Block output = Block.getBlockFromItem(recipe.mOutputs[0].getItem());
                if (output != VaultMod.CONTROLLER && output != VaultMod.ACCESS && output != VaultMod.OUTPUT) continue;
                check(recipe.mEUt == gregtech.api.enums.TierEU.RECIPE_LuV, "wrong recipe voltage " + recipe.mEUt);
                check(recipe.mEUt > gregtech.api.enums.GTValues.V[5], "IV can supply recipe");
                check(
                    recipe.mInputs.length == 4 && recipe.mFluidInputs.length == 1,
                    "missing LuV assembly ingredients");
                count++;
            }
            check(count == 3, "expected three assembler recipes, found " + count);
            for (Object entry : net.minecraft.item.crafting.CraftingManager.getInstance()
                .getRecipeList()) {
                ItemStack result = ((net.minecraft.item.crafting.IRecipe) entry).getRecipeOutput();
                if (result != null) check(
                    Block.getBlockFromItem(result.getItem()) != VaultMod.CONTROLLER
                        && Block.getBlockFromItem(result.getItem()) != VaultMod.ACCESS,
                    "low-tier crafting fallback exists");
            }
        });
        run("formed_bus_uses_LuV_shell_without_mutating_native_skin", () -> {
            IGregTechTileEntity base = bus.getBaseMetaTileEntity();
            NBTTagCompound before = new NBTTagCompound();
            bus.saveNBTData(before);
            gregtech.api.interfaces.ITexture expected = dev.aestoragevault.world.VaultSkin.texture(
                dev.aestoragevault.world.VaultStructure
                    .skinTile(world, base.getXCoord(), base.getYCoord(), base.getZCoord(), 1));
            gregtech.api.interfaces.ITexture[] skin = bus.getTexture(
                base,
                net.minecraftforge.common.util.ForgeDirection.UP,
                base.getFrontFacing(),
                -1,
                false,
                false);
            check(expected != null && skin[0] == expected, "formed input bus not continuous shell");
            NBTTagCompound after = new NBTTagCompound();
            bus.saveNBTData(after);
            check(
                before.getInteger("texturePage") == after.getInteger("texturePage")
                    && before.getInteger("textureIndex") == after.getInteger("textureIndex"),
                "saved hatch texture changed");
            world.setBlockToAir(X + 1, Y + 1, Z + 2);
            skin = bus.getTexture(
                base,
                net.minecraftforge.common.util.ForgeDirection.UP,
                base.getFrontFacing(),
                -1,
                false,
                false);
            check(skin[0] != expected, "broken structure retained formed skin");
            world.setBlock(X + 1, Y + 1, Z + 2, GregTechAPI.sBlockCasings1, 6, 3);
            controller.checkStructure();
            check(access.available(), "skin test repair failed");
        });
        run("nearby_unrelated_input_bus_keeps_native_skin", () -> {
            int x = X + 2, y = Y, z = Z + 1;
            ItemStack hatch = GregTechAPI.METATILEENTITIES[bus.getBaseMetaTileEntity()
                .getMetaTileID()].getStackForm(1);
            world.setBlockToAir(x, y, z);
            check(hatch.tryPlaceItemIntoWorld(player, world, x, y, z, 1, .5f, .5f, .5f), "unrelated bus placement");
            IGregTechTileEntity tile = (IGregTechTileEntity) world.getTileEntity(x, y, z);
            MTEHatchInputBus other = (MTEHatchInputBus) tile.getMetaTileEntity();
            check(!dev.aestoragevault.world.VaultStructure.isFormedBus(tile), "unrelated bus claimed");
            gregtech.api.interfaces.ITexture[] texture = other.getTexture(
                tile,
                net.minecraftforge.common.util.ForgeDirection.UP,
                tile.getFrontFacing(),
                -1,
                false,
                false);
            check(
                texture[0] != gregtech.api.enums.Textures.BlockIcons.casingTexturePages[0][6],
                "unrelated bus reskinned");
            world.setBlockToAir(x, y, z);
        });
        run("lower_tier_and_legacy_casings_cannot_form", () -> {
            for (int meta : new int[] { 0, 5, 7 }) {
                world.setBlock(X + 1, Y + 1, Z + 2, GregTechAPI.sBlockCasings1, meta, 3);
                controller.checkStructure();
                check(!controller.formed, "wrong casing tier accepted " + meta);
            }
            world.setBlock(X + 1, Y + 1, Z + 2, VaultMod.CASING, 0, 3);
            controller.checkStructure();
            check(!controller.formed, "legacy shell accepted");
            world.setBlock(X + 1, Y + 1, Z + 2, GregTechAPI.sBlockCasings1, 6, 3);
            controller.checkStructure();
            check(access.available(), "LuV restore failed");
        });
        run("AE_item_insert_and_extract", () -> {
            IAEItemStack request = AEItemStack.create(new ItemStack(Items.gold_ingot))
                .setStackSize(320);
            check(
                storage().getItemInventory()
                    .injectItems(request, Actionable.MODULATE, new MachineSource(access)) == null,
                "grid insertion");
            check(request.getStackSize() == 320, "input mutated");
            IAEItemStack out = storage().getItemInventory()
                .extractItems(request, Actionable.MODULATE, new MachineSource(access));
            check(out != null && out.getStackSize() == 320, "grid extraction");
        });
        run("AE_fluid_insert_and_extract", () -> {
            IAEFluidStack request = AEFluidStack.create(new FluidStack(FluidRegistry.LAVA, 10000));
            check(
                storage().getFluidInventory()
                    .injectItems(request, Actionable.MODULATE, new MachineSource(access)) == null,
                "fluid insert");
            IAEFluidStack out = storage().getFluidInventory()
                .extractItems(request, Actionable.MODULATE, new MachineSource(access));
            check(out != null && out.getStackSize() == 10000, "fluid extract");
        });
        run("no_63_type_limit_through_real_AE_handler", () -> {
            for (int i = 0; i < 1000; i++) {
                ItemStack item = new ItemStack(Items.paper);
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger("type", i);
                item.setTagCompound(tag);
                check(
                    storage().getItemInventory()
                        .injectItems(AEItemStack.create(item), Actionable.MODULATE, new MachineSource(access)) == null,
                    "type " + i);
            }
            check(
                controller.store()
                    .types() >= 1003,
                "type capped");
        });
        run("full_store_rejects_without_voiding_and_canAccept_false", () -> {
            Resource lava = ResourceCodec.encode(AEFluidStack.create(new FluidStack(FluidRegistry.LAVA, 1)));
            long n = controller.store()
                .insert(lava, Long.MAX_VALUE, false);
            check(n > 0, "no free space");
            IMEInventoryHandler<IAEItemStack> handler = (IMEInventoryHandler<IAEItemStack>) access
                .getCellArray(AEItemStackType.ITEM_STACK_TYPE)
                .get(0);
            check(!handler.canAccept(AEItemStack.create(new ItemStack(Items.diamond))), "canAccept true while full");
            check(
                handler.injectItems(
                    AEItemStack.create(new ItemStack(Items.diamond)),
                    Actionable.MODULATE,
                    new MachineSource(access)) != null,
                "overflow voided");
            controller.store()
                .extract(lava, n, false);
        });
        run("broken_casing_disables_AE_immediately", () -> {
            world.setBlockToAir(X + 1, Y + 1, Z + 2);
            check(!access.available(), "broken structure accessible");
            check(
                storage().getItemInventory()
                    .extractItems(
                        AEItemStack.create(new ItemStack(Items.iron_ingot)),
                        Actionable.MODULATE,
                        new MachineSource(access))
                    == null,
                "broken structure extracted");
            world.setBlock(X + 1, Y + 1, Z + 2, GregTechAPI.sBlockCasings1, 6, 3);
            controller.checkStructure();
            check(access.available(), "repair failed");
        });
        run("filled_center_and_duplicate_access_rejected", () -> {
            world.setBlock(X, Y, Z + 1, Blocks.stone, 0, 3);
            controller.checkStructure();
            check(!controller.formed, "filled center accepted");
            world.setBlockToAir(X, Y, Z + 1);
            world.setBlock(X + 1, Y + 1, Z + 2, VaultMod.ACCESS, 0, 3);
            controller.checkStructure();
            check(!controller.formed, "two interfaces accepted");
            world.setBlock(X + 1, Y + 1, Z + 2, GregTechAPI.sBlockCasings1, 6, 3);
            controller.checkStructure();
            check(access.available(), "reform failed");
        });
        run("NBT_keys_ignore_compound_order_but_preserve_list_order", () -> {
            ItemStack a = new ItemStack(Items.paper), b = new ItemStack(Items.paper);
            NBTTagCompound x = new NBTTagCompound(), y = new NBTTagCompound();
            x.setInteger("a", 1);
            x.setInteger("b", 2);
            y.setInteger("b", 2);
            y.setInteger("a", 1);
            a.setTagCompound(x);
            b.setTagCompound(y);
            check(
                ResourceCodec.encode(AEItemStack.create(a))
                    .equals(ResourceCodec.encode(AEItemStack.create(b))),
                "compound key differs");
            NBTTagList l = new NBTTagList();
            l.appendTag(new NBTTagString("a"));
            l.appendTag(new NBTTagString("b"));
            x.setTag("list", l);
            NBTTagList r = new NBTTagList();
            r.appendTag(new NBTTagString("b"));
            r.appendTag(new NBTTagString("a"));
            y.setTag("list", r);
            check(
                !ResourceCodec.encode(AEItemStack.create(a))
                    .equals(ResourceCodec.encode(AEItemStack.create(b))),
                "list order lost");
            Resource res = ResourceCodec.encode(AEItemStack.create(a));
            check(res.equals(ResourceCodec.encode(ResourceCodec.decode(res))), "codec roundtrip");
        });
        run("controller_NBT_excludes_inventory", () -> {
            NBTTagCompound tag = new NBTTagCompound();
            controller.writeToNBT(tag);
            java.io.ByteArrayOutputStream data = new java.io.ByteArrayOutputStream();
            CompressedStreamTools.write(tag, new java.io.DataOutputStream(data));
            check(data.size() < 512, "large controller NBT " + data.size());
            check(!tag.hasKey("Items") && !tag.hasKey("Stock"), "items leaked to controller");
        });
        run("harvested_controller_preserves_UUID_and_capacity", () -> {
            ItemStack drop = VaultMod.CONTROLLER.getDrops(world, X, Y, Z, 2, 0)
                .get(0);
            check(drop.hasTagCompound(), "missing item NBT");
            check(
                drop.getTagCompound()
                    .getString("VaultId")
                    .equals(controller.id.toString()),
                "wrong UUID");
            TileController copy = new TileController();
            copy.restoreItem(drop.getTagCompound());
            check(
                copy.id.equals(controller.id) && copy.capacity == controller.store()
                    .capacity(),
                "drop state lost");
            check(VaultMod.CONTROLLER.removedByPlayer(world, player, X, Y, Z, true), "harvest denied");
            check(world.getTileEntity(X, Y, Z) == controller, "tile removed before drops");
        });
        run("duplicate_controller_UUID_cannot_mount_same_store", () -> {
            world.setBlock(X + 4, Y, Z, VaultMod.CONTROLLER, 2, 3);
            TileController duplicate = (TileController) world.getTileEntity(X + 4, Y, Z);
            duplicate.restoreItem(controller.itemData());
            duplicate.updateEntity();
            check(duplicate.store() == null && duplicate.status.startsWith("LOCKED:"), "duplicate mounted");
            world.setBlockToAir(X + 4, Y, Z);
        });
        run("server_GUI_controls_require_distance_and_toggle_absorption", () -> {
            VaultContainer gui = new VaultContainer(player, controller);
            boolean before = controller.automatic;
            check(gui.enchantItem(player, 0), "toggle failed");
            check(controller.automatic != before, "toggle unchanged");
            gui.enchantItem(player, 0);
            player.setPosition(200, Y, 200);
            check(!gui.enchantItem(player, 0), "remote toggle accepted");
            player.setPosition(X + .5, Y, Z - 1.5);
        });
        run("missing_external_files_lock_without_creating_empty_inventory", () -> {
            world.setBlock(X + 5, Y, Z, VaultMod.CONTROLLER, 2, 3);
            TileController target = (TileController) world.getTileEntity(X + 5, Y, Z);
            NBTTagCompound tag = new NBTTagCompound();
            UUID missing = UUID.randomUUID();
            tag.setString("VaultId", missing.toString());
            tag.setLong("MaximumBytes", 1024);
            target.restoreItem(tag);
            target.updateEntity();
            check(target.store() == null && target.status.startsWith("LOCKED:"), "missing inventory mounted");
            check(
                !Files.exists(
                    world.getSaveHandler()
                        .getWorldDirectory()
                        .toPath()
                        .resolve("data/aestoragevault/" + missing + ".snapshot")),
                "created replacement file");
            world.setBlockToAir(X + 5, Y, Z);
        });
        run("invalid_UUID_NBT_does_not_crash_or_create_inventory", () -> {
            world.setBlock(X + 5, Y, Z, VaultMod.CONTROLLER, 2, 3);
            TileController target = (TileController) world.getTileEntity(X + 5, Y, Z);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("VaultId", "broken");
            tag.setLong("MaximumBytes", 1024);
            target.restoreItem(tag);
            target.updateEntity();
            check(target.store() == null && target.status.startsWith("LOCKED:"), "invalid UUID accepted");
            world.setBlockToAir(X + 5, Y, Z);
        });
        run("controller_unload_closes_repository_and_replacement_restores_inventory", () -> {
            Map<Resource, Long> before = controller.store()
                .entries();
            long bytes = controller.store()
                .capacity();
            VaultStore old = controller.store();
            controller.onChunkUnload();
            check(controller.store() == null && !old.isHealthy(), "unloaded repository retained");
            world.setBlockToAir(X, Y, Z);
            world.setBlock(X, Y, Z, VaultMod.CONTROLLER, 2, 3);
            TileController fresh = (TileController) world.getTileEntity(X, Y, Z);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("VaultId", controller.id.toString());
            tag.setLong("MaximumBytes", bytes);
            fresh.restoreItem(tag);
            fresh.updateEntity();
            fresh.checkStructure();
            check(fresh.store() != null, fresh.status);
            check(
                before.equals(
                    fresh.store()
                        .entries()),
                "reopened stock differs");
            controller = fresh;
            check(access.available(), "reopened access inactive");
        });
    }

    private void restartTests() throws Exception {
        NBTTagCompound saved = CompressedStreamTools.readCompressed(Files.newInputStream(marker));
        run(
            "fresh_server_process_restores_controller_UUID",
            () -> check(
                controller.id.toString()
                    .equals(saved.getString("Vault")),
                "UUID changed"));
        run("fresh_server_process_restores_capacity_and_all_1000_types", () -> {
            check(controller.store() != null, controller.status);
            check(
                controller.store()
                    .capacity() == saved.getLong("Bytes"),
                "capacity lost");
            check(
                controller.store()
                    .types() >= 1003,
                "types lost");
        });
        run("fresh_server_process_restores_items_and_fluids", () -> {
            check(
                controller.store()
                    .amount(ResourceCodec.encode(AEItemStack.create(new ItemStack(Items.iron_ingot))))
                    == saved.getLong("Iron"),
                "iron changed");
            check(
                controller.store()
                    .amount(ResourceCodec.encode(AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1))))
                    == 900000,
                "water changed");
        });
        run("fresh_server_process_reconnects_real_AE_network", () -> {
            check(access.available(), "access offline " + controller.status);
            check(
                storage().getItemInventory()
                    .extractItems(
                        AEItemStack.create(new ItemStack(Items.iron_ingot)),
                        Actionable.SIMULATE,
                        new MachineSource(access))
                    != null,
                "AE reconnect lost stock");
        });
    }

    private boolean hasItem(ItemStack target) {
        for (int slot = 0; slot < bus.getSizeInventory(); slot++) {
            ItemStack value = bus.getStackInSlot(slot);
            if (value != null && value.isItemEqual(target)) return true;
        }
        return false;
    }

    private IStorageGrid storage() throws Exception {
        return access.getProxy()
            .getStorage();
    }

    private <T extends IAEStack<T>> IMEInventoryHandler<T> cell(ItemStack stack, IAEStackType<T> type) {
        return AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(stack, null, type);
    }

    private TileEntity place(IBlockDefinition def, int x, int y, int z) {
        ItemStack s = def.maybeStack(1)
            .get();
        Block b = Block.getBlockFromItem(s.getItem());
        check(world.setBlock(x, y, z, b, s.getItemDamage(), 3), "AE block place");
        b.onBlockPlacedBy(world, x, y, z, player, s);
        TileEntity tile = world.getTileEntity(x, y, z);
        ((IGridProxyable) tile).getProxy()
            .setOwner(player);
        return tile;
    }

    private interface Test {

        void run() throws Exception;
    }

    private void run(String name, Test test) {
        try {
            test.run();
            record(name, true, "");
        } catch (Throwable e) {
            e.printStackTrace();
            record(name, false, e.toString());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private void record(String name, boolean passed, String detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("passed", passed);
        row.put("detail", detail);
        rows.add(row);
        System.out.println("VAULT_TEST " + name + " " + passed + " " + detail);
    }

    private void finish() throws Exception {
        done = true;
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(
            "passed",
            !rows.isEmpty() && rows.stream()
                .allMatch(row -> Boolean.TRUE.equals(row.get("passed"))));
        report.put("tests", rows);
        report.put("client_launched", false);
        report.put("java", System.getProperty("java.version"));
        report.put("restart", restart);
        Files.write(
            server.getFile(restart ? "vault-tests-restart.json" : "vault-tests.json")
                .toPath(),
            new GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(report)
                .getBytes(StandardCharsets.UTF_8));
        server.initiateShutdown();
    }
}
