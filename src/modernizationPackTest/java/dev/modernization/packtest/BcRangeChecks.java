package dev.modernization.packtest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;

import org.joml.Vector3i;

import com.google.gson.Gson;
import com.recursive_pineapple.matter_manipulator.common.building.PendingBlock;
import com.recursive_pineapple.matter_manipulator.common.building.PendingBuild;
import com.recursive_pineapple.matter_manipulator.common.items.MMItemList;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator.ManipulatorTier;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Location;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState;

import buildcraft.BuildCraftBuilders;
import buildcraft.builders.TileArchitect;
import buildcraft.builders.TileFiller;
import buildcraft.core.lib.block.TileBuildCraft;
import dev.structureterminal.bc.PlannedRange;
import dev.structureterminal.bc.PortableRange;
import dev.structureterminal.bc.RangeContainer;
import dev.structureterminal.bc.RangeJobs;
import dev.structureterminal.bc.RangeMachine;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public final class BcRangeChecks {

    static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
    }

    static void run() throws Exception {
        MinecraftServer server = MinecraftServer.getServer();
        WorldServer world = server.worldServerForDimension(0);
        Map<String, Object> report = new LinkedHashMap<>();
        List<String> tests = new ArrayList<>();
        try {
            int x = world.getSpawnPoint().posX, y = 90, z = world.getSpawnPoint().posZ;
            FakePlayer p = FakePlayerFactory.getMinecraft(world);
            p.setPosition(x + .5, y + .5, z + 2.5);
            world.setBlock(x, y, z, BuildCraftBuilders.fillerBlock, 2, 3);
            TileFiller filler = (TileFiller) world.getTileEntity(x, y, z);
            filler.updateEntity();
            RangeContainer c = RangeContainer.at(p, world, x, y, z);
            check(c != null, "missing filler range container");
            int[] first = c.preview(1, 1, 1);
            check(
                Arrays.equals(first, new int[] { x, y, z - 1, x, y, z - 1 }) && !filler.getBox()
                    .isInitialized(),
                "preview mutated selection");
            tests.add("preview_only");
            world.setBlockMetadataWithNotify(x, y, z, 5, 3);
            try {
                c.applyPreview();
                throw new AssertionError("stale face");
            } catch (IllegalArgumentException expected) {}
            tests.add("rotation_invalidates_preview");
            first = c.preview(-2, -2, 2);
            check(!c.applyPreview() && Arrays.equals(first, RangeJobs.bounds(filler)), "apply");
            filler.setInventorySlotContents(0, new ItemStack(Blocks.cobblestone, 64));
            // Allow BC to create the native template but keep the active job stalled for the queue check.
            filler.initialize();
            int[] next = c.preview(1, 1, 1);
            check(c.applyPreview() && Arrays.equals(first, RangeJobs.bounds(filler)), "in-flight replacement");
            NBTTagCompound saved = new NBTTagCompound();
            filler.writeToNBT(saved);
            TileFiller reloaded = new TileFiller();
            reloaded.readFromNBT(saved);
            check(
                Arrays.equals(first, RangeJobs.bounds(reloaded)) && Arrays.equals(
                    next,
                    ((RangeMachine) reloaded).rangeData()
                        .getIntArray(RangeJobs.PENDING)),
                "reload");
            tests.add("filler_pending_and_active_ranges_persist");
            final int[] queuedRange = next;
            final long started = world.getTotalWorldTime();
            cpw.mods.fml.common.FMLCommonHandler.instance()
                .bus()
                .register(new Watch(() -> {
                    try {
                        filler.getBattery()
                            .addEnergy(0, 1000, false);
                        if (!Arrays.equals(queuedRange, RangeJobs.bounds(filler))
                            && world.getTotalWorldTime() - started < 600) return false;
                        checkArchitect(world, p, x, y, z, queuedRange, filler, tests);
                        report.put("passed", true);
                    } catch (Throwable failure) {
                        failure.printStackTrace();
                        report.put("passed", false);
                        report.put("error", failure.toString());
                    }
                    finish(server, report, tests);
                    return true;
                }));
            return;
        } catch (Throwable failure) {
            failure.printStackTrace();
            report.put("passed", false);
            report.put("error", failure.toString());
        }
        finish(server, report, tests);
    }

    public static final class Watch {

        private final java.util.concurrent.Callable<Boolean> step;

        Watch(java.util.concurrent.Callable<Boolean> step) {
            this.step = step;
        }

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void tick(cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent event) throws Exception {
            if (event.phase == cpw.mods.fml.common.gameevent.TickEvent.Phase.END && step.call())
                cpw.mods.fml.common.FMLCommonHandler.instance()
                    .bus()
                    .unregister(this);
        }
    }

    private static void finish(MinecraftServer server, Map<String, Object> report, List<String> tests)
        throws Exception {
        report.put("tests", tests);
        Files.write(
            server.getFile("bc-range-tests.json")
                .toPath(),
            new Gson().toJson(report)
                .getBytes(StandardCharsets.UTF_8));
        server.initiateShutdown();
    }

    private static void checkArchitect(WorldServer world, FakePlayer p, int x, int y, int z, int[] next,
        TileFiller filler, List<String> tests) {
        check(
            Arrays.equals(next, RangeJobs.bounds(filler)) && world.getBlock(x + 2, y - 1, z - 1) == Blocks.cobblestone
                && world.isAirBlock(x + 3, y - 1, z - 1),
            "native filler work and pending promotion: " + Arrays.toString(RangeJobs.bounds(filler))
                + " target="
                + world.getBlock(x + 2, y - 1, z - 1)
                + " energy="
                + filler.getBattery()
                    .getEnergyStored());
        tests.add("native_filler_completes_then_promotes");
        world.setBlock(x, y, z, BuildCraftBuilders.architectBlock, 3, 3);
        TileArchitect table = (TileArchitect) world.getTileEntity(x, y, z);
        table.updateEntity();
        RangeContainer c = RangeContainer.at(p, world, x, y, z);
        check(table.mode == TileArchitect.Mode.NONE && c != null, "unmarked architect unavailable");
        int[] first = c.preview(2, -2, 2);
        check(!c.applyPreview() && table.mode == TileArchitect.Mode.COPY, "copy mode");
        table.setInventorySlotContents(0, new ItemStack(BuildCraftBuilders.templateItem));
        next = c.preview(1, 1, 1);
        check(c.applyPreview() && Arrays.equals(first, RangeJobs.bounds(table)), "active scan");
        for (int tick = 0; tick < 200; tick++) table.updateEntity();
        check(table.getStackInSlot(1) != null && Arrays.equals(next, RangeJobs.bounds(table)), "scan result");
        tests.add("unmarked_architect_scans_before_promoting");
        for (Block block : new Block[] { BuildCraftBuilders.architectBlock, BuildCraftBuilders.fillerBlock }) {
            checkTransfer(world, p, x + 3, y + 2, z + 3, block, tests);
        }
    }

    private static void checkTransfer(WorldServer world, FakePlayer player, int x, int y, int z, Block block,
        List<String> tests) {
        world.setBlockToAir(x, y, z);
        world.setBlock(x, y, z, block, 2, 3);
        int[] size = { -3, -2, 4 };
        TileEntity source = world.getTileEntity(x, y, z);
        PortableRange.apply(source, size);
        TileBuildCraft replica = block == BuildCraftBuilders.architectBlock ? new TileArchitect() : new TileFiller();
        replica.setWorldObj(world);
        ByteBuf sync = Unpooled.buffer();
        try {
            ((TileBuildCraft) source).writeData(sync);
            replica.readData(sync);
            check(
                !sync.isReadable() && Arrays.equals(size, PortableRange.dimensions(replica)),
                "preview settings sync");
        } finally {
            sync.release();
        }
        tests.add(block.getUnlocalizedName() + "_preview_packet");
        // Forge harvest path, including normal entity drops.
        player.inventory.mainInventory[player.inventory.currentItem] = new ItemStack(Items.diamond_pickaxe);
        check(block.removedByPlayer(world, player, x, y, z, true), "remove");
        block.harvestBlock(world, player, x, y, z, 2);
        check(world.isAirBlock(x, y, z), "original remains");
        AxisAlignedBB area = AxisAlignedBB.getBoundingBox(x - 1, y - 1, z - 1, x + 2, y + 2, z + 2);
        ItemStack drop = null;
        for (Object value : world.getEntitiesWithinAABB(EntityItem.class, area)) {
            EntityItem entity = (EntityItem) value;
            ItemStack stack = entity.getEntityItem();
            if (stack.getItem() == Item.getItemFromBlock(block)) {
                drop = stack;
                entity.setDead();
                break;
            }
        }
        check(
            drop != null && drop.hasTagCompound()
                && Arrays.equals(
                    size,
                    drop.getTagCompound()
                        .getIntArray(PortableRange.KEY)),
            "drop settings " + block);
        player.rotationYaw = 90;
        world.setBlock(x, y - 1, z, Blocks.stone);
        check(drop.tryPlaceItemIntoWorld(player, world, x, y - 1, z, 1, .5f, 1f, .5f), "place");
        source = world.getTileEntity(x, y, z);
        check(
            PortableRange.front(source) == 5
                && Arrays.equals(RangeJobs.bounds(source), new int[] { x + 1, y - 1, z - 2, x + 4, y, z }),
            "placed east");
        tests.add(block.getUnlocalizedName() + "_harvest_reinstall");
        ItemStack tool = MMItemList.MK3.get();
        MMState state = ItemMatterManipulator.getState(tool);
        state.config.placeMode = MMState.PlaceMode.COPYING;
        state.config.coordA = new Location(world, x, y, z);
        state.config.coordB = state.config.coordA;
        int targetX = x + 5;
        state.config.coordC = new Location(world, targetX, y, z);
        state.config.arraySpan = new Vector3i(1, 0, 0);
        state.getTransform()
            .rotate(ForgeDirection.UP, 1);
        int facing = state.getTransform()
            .apply(ForgeDirection.EAST)
            .ordinal();
        check(facing == 2 || facing == 3, "quarter turn");
        ManipulatorTier tier = ManipulatorTier.Tier3;
        List<PendingBlock> plan = state.getPendingBlocks(tier, world);
        check(plan.size() == 2, "array plan " + plan.size());
        List<int[]> previews = new ArrayList<>();
        for (PendingBlock b : plan) {
            world.setBlockToAir(b.x, b.y, b.z);
            int[] preview = ((PlannedRange) b).machineRange()
                .bounds(world, b.x, b.y, b.z);
            check(
                Arrays.equals(preview, PortableRange.bounds(b.x, b.y, b.z, facing, size, world.getHeight())),
                "rotated preview");
            previews.add(preview);
        }
        player.capabilities.isCreativeMode = true;
        try {
            new PendingBuild(player, state, tier, plan).tryPlaceBlocks(tool, player);
        } finally {
            player.capabilities.isCreativeMode = false;
        }
        int i = 0;
        for (PendingBlock b : plan) {
            TileEntity tile = world.getTileEntity(b.x, b.y, b.z);
            check(
                tile instanceof RangeMachine && PortableRange.front(tile) == facing
                    && Arrays.equals(RangeJobs.bounds(tile), previews.get(i++)),
                "MM paste/preview mismatch at " + b.x + "," + b.z);
            world.setBlockToAir(b.x, b.y, b.z);
        }
        tests.add(block.getUnlocalizedName() + "_mm_rotated_array_preview_and_build");
    }

}
