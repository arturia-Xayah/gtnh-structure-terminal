package dev.aeoptimizer.packtest;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.BlockEvent;

import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEColor;
import appeng.helpers.IInterfaceHost;
import appeng.tile.misc.TileInterface;
import appeng.tile.storage.TileDrive;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import dev.structureterminal.packing.PackingItems;
import dev.structureterminal.packing.PackingService;

/** Real devices in a dedicated GTNH server: no replacement inventory or client simulation. */
final class PackingPackChecks {

    private static IPartHost liveHost;
    private static IInterfaceHost restored;
    private static ItemStack output;
    private static TileDrive restoredDrive;

    static void setup(OptimizerPackTest s) {
        s.world.setBlock(
            24,
            80,
            8,
            AEApi.instance()
                .definitions()
                .blocks()
                .controller()
                .maybeBlock()
                .get(),
            0,
            3);
        s.world.setBlock(
            23,
            80,
            8,
            AEApi.instance()
                .definitions()
                .blocks()
                .energyCellCreative()
                .maybeBlock()
                .get(),
            0,
            3);
        s.world.setBlock(
            24,
            80,
            9,
            AEApi.instance()
                .definitions()
                .blocks()
                .security()
                .maybeBlock()
                .get(),
            0,
            3);
        ((appeng.me.helpers.IGridProxyable) s.world.getTileEntity(24, 80, 9)).getProxy()
            .setOwner(s.player);
        liveHost = host(s, 25, 80, 8);
        liveHost.addPart(iface(), ForgeDirection.NORTH, s.player);
        liveHost.addPart(iface(), ForgeDirection.SOUTH, s.player);
        output = OptimizerPackTest.token("packed-interface-result");
        IInterfaceHost original = (IInterfaceHost) liveHost.getPart(ForgeDirection.NORTH);
        original.getInterfaceDuality()
            .getPatterns()
            .setInventorySlotContents(
                0,
                s.pattern(new ItemStack[] { new ItemStack(Items.iron_ingot) }, output)
                    .getPattern());
        ItemStack integrated = new ItemStack(dev.structureterminal.pattern.IntegratedPattern.ITEM);
        ItemStack[] children = new ItemStack[36];
        children[35] = s
            .pattern(
                new ItemStack[] { new ItemStack(Items.iron_ingot) },
                OptimizerPackTest.token("packed-integrated-result"))
            .getPattern();
        dev.structureterminal.pattern.IntegratedPattern.write(integrated, children);
        original.getPatterns()
            .setInventorySlotContents(1, integrated);
        original.getInterfaceDuality()
            .getUpgrades()
            .setInventorySlotContents(
                0,
                AEApi.instance()
                    .definitions()
                    .materials()
                    .cardPatternCapacity()
                    .maybeStack(1)
                    .get());
        original.getInterfaceDuality()
            .setPriority(17);
        original.getInterfaceDuality()
            .getInternalInventory()
            .setInventorySlotContents(0, new ItemStack(Items.diamond, 13));
    }

    static void run(OptimizerPackTest s) throws Exception {
        s.player.setPosition(25, 81, 8);
        s.player.setSneaking(true);
        ItemStack tape = new ItemStack(PackingItems.TAPE);
        s.player.inventory.mainInventory[s.player.inventory.currentItem] = tape;
        IPart other = liveHost.getPart(ForgeDirection.SOUTH);
        IPart cable = liveHost.getPart(ForgeDirection.UNKNOWN);
        verify(
            s,
            "live_source_ready",
            liveHost.getPart(ForgeDirection.NORTH)
                .getGridNode() != null);
        appeng.api.networking.security.ISecurityGrid security = liveHost.getPart(ForgeDirection.NORTH)
            .getGridNode()
            .getGrid()
            .getCache(appeng.api.networking.security.ISecurityGrid.class);
        verify(s, "native_security_active", security.isAvailable());
        net.minecraftforge.common.util.FakePlayer intruder = new net.minecraftforge.common.util.FakePlayer(
            s.world,
            new com.mojang.authlib.GameProfile(
                java.util.UUID.fromString("ae000000-0000-4000-8000-000000000099"),
                "PackingIntruder"));
        intruder.setPosition(25, 81, 8);
        verify(
            s,
            "native_security_denies_packing",
            !PackingService.pack(tape, intruder, s.world, 25, 80, 8, 2, .5f, .5f, .01f)
                && liveHost.getPart(ForgeDirection.NORTH) != null
                && tape.getItemDamage() == 0);
        IInterfaceHost source = (IInterfaceHost) liveHost.getPart(ForgeDirection.NORTH);
        source.getInterfaceDuality()
            .getInternalInventory()
            .setInventorySlotContents(0, new ItemStack(Items.diamond, 13));
        NBTTagCompound before = new NBTTagCompound();
        liveHost.getPart(ForgeDirection.NORTH)
            .writeToNBT(before);
        verify(s, "part_pack", PackingItems.TAPE.onItemUseFirst(tape, s.player, s.world, 25, 80, 8, 2, .5f, .5f, .01f));
        verify(
            s,
            "only_selected_part_removed",
            liveHost.getPart(ForgeDirection.NORTH) == null && liveHost.getPart(ForgeDirection.SOUTH) == other
                && liveHost.getPart(ForgeDirection.UNKNOWN) == cable);
        ItemStack pack = collect(s, 25, 80, 8);
        verify(
            s,
            "full_part_nbt_preserved",
            pack.getTagCompound()
                .getCompoundTag("contents")
                .equals(before));
        verify(s, "one_tape_use", tape.getItemDamage() == 1);
        // Force a disk-equivalent NBT round trip before placing it on a running cable bus.
        pack = ItemStack.loadItemStackFromNBT(
            CompressedStreamTools.func_152457_a(
                CompressedStreamTools.compress(pack.writeToNBT(new NBTTagCompound())),
                new net.minecraft.nbt.NBTSizeTracker(16L << 20)));
        verify(
            s,
            "native_security_denies_unpacking",
            !PackingService.unpack(pack, intruder, s.world, 25, 80, 8, 2) && pack.stackSize == 1);
        verify(s, "part_unpack_live_host", PackingService.unpack(pack, s.player, s.world, 25, 80, 8, 2));
        restored = (IInterfaceHost) liveHost.getPart(ForgeDirection.NORTH);
        verify(
            s,
            "integrated_holder_restored",
            dev.structureterminal.pattern.IntegratedPattern.count(
                restored.getPatterns()
                    .getStackInSlot(1))
                == 1);

        verify(
            s,
            "package_consumed_once",
            pack.stackSize == 0 && !PackingService.unpack(pack, s.player, s.world, 25, 80, 8, 2));
        verify(
            s,
            "pattern_upgrade_priority",
            restored.getInterfaceDuality()
                .getPatterns()
                .getStackInSlot(0) != null
                && restored.getInterfaceDuality()
                    .getUpgrades()
                    .getStackInSlot(0) != null
                && restored.getInterfaceDuality()
                    .getPriority() == 17);
        verify(
            s,
            "buffer_preserved",
            restored.getInterfaceDuality()
                .getInternalInventory()
                .getStackInSlot(0).stackSize == 13);
        verify(
            s,
            "grid_rejoined",
            liveHost.getPart(ForgeDirection.NORTH)
                .getGridNode() != null);

        s.world.setBlock(
            27,
            80,
            8,
            AEApi.instance()
                .definitions()
                .blocks()
                .iface()
                .maybeBlock()
                .get(),
            0,
            3);
        TileInterface tile = (TileInterface) s.world.getTileEntity(27, 80, 8);
        tile.setCustomName("Packed interface");
        tile.setPriority(-71);
        tile.setOrientation(ForgeDirection.WEST, ForgeDirection.UP);
        tile.getInterfaceDuality()
            .getPatterns()
            .setInventorySlotContents(
                0,
                s.pattern(new ItemStack[] { new ItemStack(Items.gold_ingot) }, OptimizerPackTest.token("packed-block"))
                    .getPattern());
        verify(s, "block_pack", PackingService.pack(tape, s.player, s.world, 27, 80, 8, 1, .5f, 1, .5f));
        ItemStack blockPack = collect(s, 27, 80, 8);
        verify(
            s,
            "block_removed_without_loose_patterns",
            s.world.isAirBlock(27, 80, 8) && drops(s, 27, 80, 8).isEmpty());
        verify(s, "block_unpack", PackingService.unpack(blockPack, s.player, s.world, 27, 80, 9, 1));
        TileInterface replacement = (TileInterface) s.world.getTileEntity(27, 80, 9);
        verify(
            s,
            "block_settings_orientation_coordinates",
            replacement.getPriority() == -71 && replacement.getForward() == ForgeDirection.WEST
                && replacement.xCoord == 27
                && replacement.zCoord == 9
                && replacement.getCustomName()
                    .equals("Packed interface")
                && replacement.getInterfaceDuality()
                    .getPatterns()
                    .getStackInSlot(0) != null);

        s.world.setBlock(
            29,
            80,
            8,
            AEApi.instance()
                .definitions()
                .blocks()
                .drive()
                .maybeBlock()
                .get(),
            0,
            3);
        TileDrive drive = (TileDrive) s.world.getTileEntity(29, 80, 8);
        ItemStack cell = AEApi.instance()
            .definitions()
            .items()
            .cell1k()
            .maybeStack(1)
            .get();
        AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(cell, null, appeng.api.storage.StorageChannel.ITEMS)
            .injectItems(
                AEItemStack.create(new ItemStack(Items.emerald, 23)),
                appeng.api.config.Actionable.MODULATE,
                s.source);
        drive.getInternalInventory()
            .setInventorySlotContents(0, cell);
        verify(s, "drive_pack", PackingService.pack(tape, s.player, s.world, 29, 80, 8, 1, .5f, 1, .5f));
        ItemStack drivePack = collect(s, 29, 80, 8);
        verify(s, "drive_unpack", PackingService.unpack(drivePack, s.player, s.world, 29, 80, 9, 1));
        restoredDrive = (TileDrive) s.world.getTileEntity(29, 80, 9);
        verify(
            s,
            "drive_cell_nbt",
            ItemStack.areItemStacksEqual(
                cell,
                restoredDrive.getInternalInventory()
                    .getStackInSlot(0)));

        ItemStack fc = new ItemStack(GameRegistry.findItem("ae2fc", "part_fluid_interface"));
        IPartHost fcHost = host(s, 31, 80, 8);
        fcHost.addPart(fc, ForgeDirection.NORTH, s.player);
        IPart fcPart = fcHost.getPart(ForgeDirection.NORTH);
        ((IInterfaceHost) fcPart).getInterfaceDuality()
            .getPatterns()
            .setInventorySlotContents(
                0,
                s.fluidPattern()
                    .getPattern());
        Object tanks = fcPart.getClass()
            .getMethod("getInternalFluid")
            .invoke(fcPart);
        int filled = (Integer) tanks.getClass()
            .getMethod("fill", int.class, net.minecraftforge.fluids.FluidStack.class, boolean.class)
            .invoke(
                tanks,
                0,
                new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 1234),
                true);
        verify(s, "dual_fixture_contains_fluid", filled == 1234);
        NBTTagCompound fcBefore = new NBTTagCompound();
        fcPart.writeToNBT(fcBefore);
        verify(s, "dual_part_pack", PackingService.pack(tape, s.player, s.world, 31, 80, 8, 2, .5f, .5f, .01f));
        ItemStack fcPack = collect(s, 31, 80, 8);
        verify(
            s,
            "dual_fluid_full_nbt",
            fcBefore.equals(
                fcPack.getTagCompound()
                    .getCompoundTag("contents")));
        verify(s, "part_place_empty_space", PackingService.unpack(fcPack, s.player, s.world, 31, 80, 9, 2));
        IPartHost newHost = (IPartHost) s.world.getTileEntity(31, 80, 9);
        NBTTagCompound fcAfter = new NBTTagCompound();
        newHost.getPart(ForgeDirection.NORTH)
            .writeToNBT(fcAfter);
        verify(s, "dual_fluid_and_patterns_restored", fcBefore.equals(fcAfter));

        Block fcBlock = null;
        for (Object value : Block.blockRegistry) if (value.getClass()
            .getName()
            .equals("com.glodblock.github.common.block.BlockFluidInterface")) fcBlock = (Block) value;
        OptimizerPackTest.check(fcBlock != null, "dual block registered");
        s.world.setBlock(30, 80, 12, fcBlock, 0, 3);
        TileEntity fcTile = s.world.getTileEntity(30, 80, 12);
        ((IInterfaceHost) fcTile).getInterfaceDuality()
            .getPatterns()
            .setInventorySlotContents(
                0,
                s.fluidPattern()
                    .getPattern());
        Object blockTanks = fcTile.getClass()
            .getMethod("getInternalFluid")
            .invoke(fcTile);
        blockTanks.getClass()
            .getMethod("fill", int.class, net.minecraftforge.fluids.FluidStack.class, boolean.class)
            .invoke(
                blockTanks,
                0,
                new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 5678),
                true);
        NBTTagCompound blockBefore = new NBTTagCompound();
        fcTile.writeToNBT(blockBefore);
        verify(s, "dual_block_pack", PackingService.pack(tape, s.player, s.world, 30, 80, 12, 1, .5f, 1, .5f));
        ItemStack fcBlockPack = collect(s, 30, 80, 12);
        verify(s, "dual_block_unpack", PackingService.unpack(fcBlockPack, s.player, s.world, 30, 80, 13, 1));
        NBTTagCompound blockAfter = new NBTTagCompound();
        s.world.getTileEntity(30, 80, 13)
            .writeToNBT(blockAfter);
        verify(
            s,
            "dual_block_fluid_restored",
            blockBefore.getCompoundTag("FluidInv")
                .equals(blockAfter.getCompoundTag("FluidInv"))
                && ((IInterfaceHost) s.world.getTileEntity(30, 80, 13)).getInterfaceDuality()
                    .getPatterns()
                    .getStackInSlot(0) != null);

        rejectionChecks(s, tape);
        s.player.setSneaking(false);
        s.player.setPosition(8, 81, 8);
        s.player.inventory.mainInventory[s.player.inventory.currentItem] = null;
    }

    static void settled(OptimizerPackTest s) {
        ICraftingGrid grid = liveHost.getPart(ForgeDirection.NORTH)
            .getGridNode()
            .getGrid()
            .getCache(ICraftingGrid.class);
        verify(
            s,
            "restored_pattern_advertised",
            !grid.getCraftingFor(AEItemStack.create(output), null, 0, s.world)
                .isEmpty());
        verify(s, "restored_drive_valid", !restoredDrive.isInvalid());
    }

    private static void rejectionChecks(OptimizerPackTest s, ItemStack tape) {
        Guard guard = new Guard();
        MinecraftForge.EVENT_BUS.register(guard);
        try {
            guard.breaking = true;
            int damage = tape.getItemDamage();
            verify(
                s,
                "break_protection_no_loss",
                !PackingService.pack(tape, s.player, s.world, 25, 80, 8, 2, .5f, .5f, .01f)
                    && liveHost.getPart(ForgeDirection.NORTH) != null
                    && tape.getItemDamage() == damage);
            guard.breaking = false;
            guard.drops = true;
            verify(
                s,
                "drop_veto_no_loss",
                !PackingService.pack(tape, s.player, s.world, 25, 80, 8, 2, .5f, .5f, .01f)
                    && liveHost.getPart(ForgeDirection.NORTH) != null
                    && tape.getItemDamage() == damage);
            guard.drops = false;
            verify(
                s,
                "unsupported_cable_rejected",
                !PackingService.pack(tape, s.player, s.world, 25, 80, 8, 1, .5f, .5f, .5f));
            IPartHost host = host(s, 20, 80, 10);
            host.addPart(iface(), ForgeDirection.NORTH, s.player);
            verify(s, "last_use_packs", PackingService.pack(tape, s.player, s.world, 20, 80, 10, 2, .5f, .5f, .01f));
            ItemStack pack = collect(s, 20, 80, 10);
            guard.placing = true;
            verify(
                s,
                "place_protection_keeps_package",
                !PackingService.unpack(pack, s.player, s.world, 20, 80, 11, 2) && pack.stackSize == 1
                    && s.world.isAirBlock(20, 80, 11));
            guard.placing = false;
            s.world.setBlock(20, 80, 11, Blocks.stone);
            s.world.setBlock(20, 80, 10, Blocks.stone);
            verify(
                s,
                "blocked_target_keeps_package",
                !PackingService.unpack(pack, s.player, s.world, 20, 80, 11, 2) && pack.stackSize == 1);
            s.world.setBlockToAir(20, 80, 11);
            boolean creative = s.player.capabilities.isCreativeMode;
            s.player.capabilities.isCreativeMode = true;
            verify(
                s,
                "creative_unpack_consumes",
                PackingService.unpack(pack, s.player, s.world, 20, 80, 11, 2) && pack.stackSize == 0);
            s.player.capabilities.isCreativeMode = creative;
            ItemStack lastTape = new ItemStack(PackingItems.TAPE, 1, 63);
            verify(
                s,
                "exactly_64_uses",
                PackingService.pack(lastTape, s.player, s.world, 20, 80, 11, 2, .5f, .5f, .01f)
                    && lastTape.stackSize == 0);
            collect(s, 20, 80, 11);
            verify(
                s,
                "invalid_package_rejected",
                !PackingService.unpack(new ItemStack(PackingItems.PACKAGE), s.player, s.world, 20, 80, 11, 2));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(guard);
        }
    }

    public static final class Guard {

        boolean breaking, placing, drops;

        @SubscribeEvent
        public void breakBlock(BlockEvent.BreakEvent e) {
            if (breaking) e.setCanceled(true);
        }

        @SubscribeEvent
        public void placeBlock(BlockEvent.PlaceEvent e) {
            if (placing) e.setCanceled(true);
        }

        @SubscribeEvent
        public void spawn(EntityJoinWorldEvent e) {
            if (drops && e.entity instanceof EntityItem) e.setCanceled(true);
        }
    }

    private static IPartHost host(OptimizerPackTest s, int x, int y, int z) {
        Block block = AEApi.instance()
            .definitions()
            .blocks()
            .multiPart()
            .maybeBlock()
            .get();
        s.world.setBlock(x, y, z, block, 0, 3);
        IPartHost host = (IPartHost) s.world.getTileEntity(x, y, z);
        host.addPart(
            AEApi.instance()
                .definitions()
                .parts()
                .cableGlass()
                .stack(AEColor.Transparent, 1),
            ForgeDirection.UNKNOWN,
            s.player);
        return host;
    }

    private static ItemStack iface() {
        return AEApi.instance()
            .definitions()
            .parts()
            .iface()
            .maybeStack(1)
            .get();
    }

    @SuppressWarnings("unchecked")
    private static List<EntityItem> drops(OptimizerPackTest s, int x, int y, int z) {
        List<EntityItem> result = s.world
            .getEntitiesWithinAABB(EntityItem.class, AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1));
        result.removeIf(e -> e.isDead);
        return result;
    }

    private static ItemStack collect(OptimizerPackTest s, int x, int y, int z) {
        List<EntityItem> list = drops(s, x, y, z);
        OptimizerPackTest.check(
            list.size() == 1 && list.get(0)
                .getEntityItem()
                .getItem() == PackingItems.PACKAGE,
            "single package drop");
        ItemStack result = list.get(0)
            .getEntityItem()
            .copy();
        list.get(0)
            .setDead();
        return result;
    }

    private static void verify(OptimizerPackTest s, String name, boolean ok) {
        OptimizerPackTest.check(ok, "packing_" + name);
        s.row("packing_" + name, true);
    }
}
