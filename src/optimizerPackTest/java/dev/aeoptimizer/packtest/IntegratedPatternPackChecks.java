package dev.aeoptimizer.packtest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Future;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.config.CraftingMode;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.parts.IInterfaceTerminal;
import appeng.api.parts.IPartHost;
import appeng.container.ContainerNull;
import appeng.container.implementations.ContainerInterface;
import appeng.container.implementations.ContainerInterfaceTerminal;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.InventoryAction;
import appeng.items.tools.ToolMemoryCard;
import appeng.me.helpers.IGridProxyable;
import appeng.tile.misc.TileInterface;
import appeng.util.inv.MEInventoryCrafting;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.common.registry.GameRegistry;
import dev.structureterminal.pattern.IntegratedPattern;
import dev.structureterminal.pattern.IntegratedPatternContainer;

/** Native AE/FC in the real pack. No hand-written crafting provider replaces the interfaces under test. */
final class IntegratedPatternPackChecks {

    private static IInterfaceHost block, dual, part, dualPart;
    private static IInterfaceTerminal terminal;
    private static ItemStack full;
    private static Future<ICraftingJob> planning;
    private static int planningPhase;
    private static int settle;

    static void setup(OptimizerPackTest s) throws Exception {
        s.world.setBlock(
            40,
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
            39,
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
            41,
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
        block = (IInterfaceHost) s.world.getTileEntity(41, 80, 8);
        s.world.setBlock(40, 80, 9, GameRegistry.findBlock("ae2fc", "fluid_interface"), 0, 3);
        dual = (IInterfaceHost) s.world.getTileEntity(40, 80, 9);
        IPartHost cable = cable(s, 40, 80, 7);
        cable.addPart(
            AEApi.instance()
                .definitions()
                .parts()
                .iface()
                .maybeStack(1)
                .get(),
            ForgeDirection.EAST,
            s.player);
        cable.addPart(
            new ItemStack(GameRegistry.findItem("ae2fc", "part_fluid_interface")),
            ForgeDirection.WEST,
            s.player);
        part = (IInterfaceHost) cable.getPart(ForgeDirection.EAST);
        dualPart = (IInterfaceHost) cable.getPart(ForgeDirection.WEST);
        cable.addPart(
            AEApi.instance()
                .definitions()
                .parts()
                .interfaceTerminal()
                .maybeStack(1)
                .get(),
            ForgeDirection.UP,
            s.player);
        terminal = (IInterfaceTerminal) cable.getPart(ForgeDirection.UP);
        full = new ItemStack(IntegratedPattern.ITEM);
        ItemStack[] children = new ItemStack[36];
        for (int i = 0; i < 34; i++)
            children[i] = s.pattern(new ItemStack[] { new ItemStack(Items.iron_ingot) }, output(i))
                .getPattern();
        children[34] = s.fluidPattern()
            .getPattern();
        children[35] = s.pattern(new ItemStack[] { new ItemStack(Blocks.log) }, new ItemStack(Blocks.planks, 4))
            .getPattern();
        children[35].getTagCompound()
            .setBoolean("crafting", true);
        IntegratedPattern.write(full, children);
        block.getPatterns()
            .setInventorySlotContents(0, full.copy());
        dual.getPatterns()
            .setInventorySlotContents(0, full.copy());
        part.getPatterns()
            .setInventorySlotContents(0, full.copy());
        dualPart.getPatterns()
            .setInventorySlotContents(0, full.copy());
        s.world.setBlock(42, 80, 8, Blocks.chest, 0, 3);
    }

    private static IPartHost cable(OptimizerPackTest s, int x, int y, int z) {
        s.world.setBlock(
            x,
            y,
            z,
            AEApi.instance()
                .definitions()
                .blocks()
                .multiPart()
                .maybeBlock()
                .get(),
            0,
            3);
        IPartHost host = (IPartHost) s.world.getTileEntity(x, y, z);
        host.addPart(
            AEApi.instance()
                .definitions()
                .parts()
                .cableGlass()
                .stack(appeng.api.util.AEColor.Transparent, 1),
            ForgeDirection.UNKNOWN,
            s.player);
        return host;
    }

    static void run(OptimizerPackTest s) throws Exception {
        ItemStack[] backup = s.player.inventory.mainInventory.clone();
        ItemStack cursor = s.player.inventory.getItemStack();
        int selected = s.player.inventory.currentItem;
        try {
            Arrays.fill(s.player.inventory.mainInventory, null);
            s.player.inventory.currentItem = 0;
            s.player.inventory.setItemStack(null);
            s.player.inventory.mainInventory[0] = new ItemStack(IntegratedPattern.ITEM);
            // Exercise the actual C08 handler: it copies the held stack AFTER onItemRightClick.
            ItemStack beforeClick = s.player.getHeldItem();
            s.player.openContainer = s.player.inventoryContainer;
            net.minecraft.network.PacketBuffer packetBytes = new net.minecraft.network.PacketBuffer(
                io.netty.buffer.Unpooled.buffer());
            packetBytes.writeInt(-1)
                .writeByte(255)
                .writeInt(-1)
                .writeByte(255);
            packetBytes.writeItemStackToBuffer(beforeClick);
            packetBytes.writeByte(0)
                .writeByte(0)
                .writeByte(0);
            net.minecraft.network.play.client.C08PacketPlayerBlockPlacement click = new net.minecraft.network.play.client.C08PacketPlayerBlockPlacement();
            click.readPacketData(packetBytes);
            packetBytes.release();
            s.player.playerNetServerHandler.processPlayerBlockPlacement(click);
            check(
                s,
                "right_click_copies_stack_before_open",
                s.player.getHeldItem() != beforeClick && s.player.openContainer == s.player.inventoryContainer);
            dev.structureterminal.pattern.IntegratedPatternOpening.INSTANCE.tick(
                new cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent(
                    cpw.mods.fml.common.gameevent.TickEvent.Phase.END));
            check(s, "right_click_opens_registered_gui", s.player.openContainer instanceof IntegratedPatternContainer);
            IntegratedPatternContainer ui = (IntegratedPatternContainer) s.player.openContainer;
            check(s, "right_click_gui_stays_open", ui.canInteractWith(s.player) && ui.holder == s.player.getHeldItem());
            check(s, "36_real_slots", ui.patterns.getSizeInventory() == 36 && ui.inventorySlots.size() == 72);
            check(
                s,
                "reject_blank_nested_junk",
                !ui.patterns.isItemValidForSlot(0, blank(1)) && !ui.patterns.isItemValidForSlot(0, full)
                    && !ui.patterns.isItemValidForSlot(0, new ItemStack(Items.diamond)));
            ItemStack[] children = IntegratedPattern.read(full);
            check(
                s,
                "accept_processing_fluid_crafting",
                ui.patterns.isItemValidForSlot(0, children[0]) && ui.patterns.isItemValidForSlot(0, children[34])
                    && ui.patterns.isItemValidForSlot(0, children[35]));
            ItemStack many = children[0].copy();
            many.stackSize = 36;
            s.player.inventory.mainInventory[9] = many;
            ui.transferStackInSlot(s.player, 36);
            check(
                s,
                "shift_fill_36_one_per_slot",
                IntegratedPattern.count(ui.holder) == 36 && s.player.inventory.mainInventory[9] == null);
            check(
                s,
                "holder_click_throw_and_number_locked",
                ui.slotClick(63, 0, 0, s.player) == null && ui.slotClick(63, 1, 4, s.player) == null
                    && ui.slotClick(0, 0, 2, s.player) == null
                    && s.player.inventory.mainInventory[0] == ui.holder);
            ui.transferStackInSlot(s.player, 35);
            check(s, "shift_extract_saved_immediately", IntegratedPattern.count(ui.holder) == 35);
            ItemStack encoded = ItemStack.loadItemStackFromNBT(ui.holder.writeToNBT(new NBTTagCompound()));
            check(
                s,
                "nbt_roundtrip",
                IntegratedPattern.count(encoded) == 35
                    && ItemStack.areItemStacksEqual(IntegratedPattern.read(encoded)[0], children[0]));
            s.player.inventory.mainInventory[0] = ui.holder.copy();
            check(
                s,
                "removed_holder_cannot_be_edited",
                !ui.canInteractWith(s.player) && ui.slotClick(0, 0, 0, s.player) == null);

            for (IInterfaceHost host : new IInterfaceHost[] { block, dual, part, dualPart }) {
                host.getInterfaceDuality()
                    .updateCraftingList();
                check(
                    s,
                    "expand_36_" + host.getClass()
                        .getSimpleName(),
                    host.getInterfaceDuality().craftingList.size() == 36);
                ICraftingPatternDetails first = host.getInterfaceDuality().craftingList.get(0);
                host.getInterfaceDuality()
                    .updateCraftingList();
                check(
                    s,
                    "stable_recipe_identity_" + host.getClass()
                        .getSimpleName(),
                    host.getInterfaceDuality().craftingList.size() == 36
                        && host.getInterfaceDuality().craftingList.get(0) == first);
                ContainerInterface slots = new ContainerInterface(s.player.inventory, host);
                Slot slot = interfaceSlot(slots, host, 0);
                check(
                    s,
                    "native_slot_accepts_" + host.getClass()
                        .getSimpleName(),
                    slot.isItemValid(full));
                Slot disabled = interfaceSlot(slots, host, 9);
                check(
                    s,
                    "disabled_slot_stays_disabled_" + host.getClass()
                        .getSimpleName(),
                    !disabled.isItemValid(full));
            }
            block.getInterfaceDuality()
                .setPriority(7);
            check(
                s,
                "native_priority",
                block.getInterfaceDuality().craftingList.get(0)
                    .getPriority() == -36 * 7);
            block.getInterfaceDuality()
                .setPriority(0);
            ICraftingPatternDetails first = block.getInterfaceDuality().craftingList.get(0);
            MEInventoryCrafting table = new MEInventoryCrafting(new ContainerNull(), 1, 1);
            table.setInventorySlotContents(0, AEItemStack.create(new ItemStack(Items.iron_ingot)));
            check(
                s,
                "native_dispatch_into_chest",
                block.getInterfaceDuality()
                    .pushPattern(first, table)
                    && ((TileEntityChest) s.world.getTileEntity(42, 80, 8)).getStackInSlot(0)
                        .getItem() == Items.iron_ingot);
            ArrayList<ItemStack> drops = new ArrayList<>();
            block.getInterfaceDuality()
                .addDrops(drops);
            check(
                s,
                "drops_holder_once",
                drops.stream()
                    .filter(IntegratedPattern::is)
                    .count() == 1
                    && IntegratedPattern.count(
                        drops.stream()
                            .filter(IntegratedPattern::is)
                            .findFirst()
                            .get())
                        == 36);
            NBTTagCompound tile = new NBTTagCompound();
            ((TileInterface) block).writeToNBT(tile);
            TileInterface restored = new TileInterface();
            restored.readFromNBT(tile);
            check(
                s,
                "native_tile_nbt",
                IntegratedPattern.count(
                    restored.getPatterns()
                        .getStackInSlot(0))
                    == 36);
            memory(s);
            terminal(s);
            recipe(s);
            // Unsupported pattern consumers must see an ordinary container item, not a fake recipe.
            check(
                s,
                "no_fake_single_recipe",
                !((Object) IntegratedPattern.ITEM instanceof appeng.api.implementations.ICraftingPatternItem));
            Object analysis = Class
                .forName("com.recursive_pineapple.matter_manipulator.common.building.InventoryAnalysis")
                .getMethod("fromInventory", net.minecraft.inventory.IInventory.class, boolean.class)
                .invoke(null, block.getPatterns(), false);
            Object[] providers = (Object[]) analysis.getClass()
                .getField("mItems")
                .get(analysis);
            check(
                s,
                "matter_manipulator_requires_physical_holder",
                providers[0].getClass()
                    .getSimpleName()
                    .equals("PortableItemStack"));
        } finally {
            s.player.openContainer = s.player.inventoryContainer;
            System.arraycopy(backup, 0, s.player.inventory.mainInventory, 0, backup.length);
            s.player.inventory.currentItem = selected;
            s.player.inventory.setItemStack(cursor);
        }
    }

    private static Slot interfaceSlot(ContainerInterface gui, IInterfaceHost host, int index) {
        for (Object value : gui.inventorySlots) {
            Slot slot = (Slot) value;
            if (slot.inventory == host.getPatterns() && slot.getSlotIndex() == index) return slot;
        }
        throw new AssertionError("Missing native pattern slot " + index);
    }

    private static void memory(OptimizerPackTest s) {
        NBTTagCompound data = new NBTTagCompound();
        ToolMemoryCard.savePatterns(data, block);
        check(
            s,
            "memory_saves_holder",
            data.getTagList("interfacePatterns", 10)
                .tagCount() == 1);
        dual.getPatterns()
            .setInventorySlotContents(0, null);
        Arrays.fill(s.player.inventory.mainInventory, null);
        s.player.inventory.mainInventory[1] = blank(36);
        ToolMemoryCard.insertPatterns(data, s.player, dual);
        check(
            s,
            "memory_requires_empty_holder",
            dual.getPatterns()
                .getStackInSlot(0) == null && s.player.inventory.mainInventory[1].stackSize == 36);
        s.player.inventory.mainInventory[0] = new ItemStack(IntegratedPattern.ITEM);
        s.player.inventory.mainInventory[1] = blank(35);
        ToolMemoryCard.insertPatterns(data, s.player, dual);
        check(
            s,
            "memory_underpayment_atomic",
            dual.getPatterns()
                .getStackInSlot(0) == null && s.player.inventory.mainInventory[0] != null
                && s.player.inventory.mainInventory[1].stackSize == 35);
        s.player.inventory.mainInventory[1] = blank(36);
        ToolMemoryCard.insertPatterns(data, s.player, dual);
        check(
            s,
            "memory_consumes_holder_plus_36_blanks",
            IntegratedPattern.count(
                dual.getPatterns()
                    .getStackInSlot(0))
                == 36 && s.player.inventory.mainInventory[0] == null
                && s.player.inventory.mainInventory[1] == null);
    }

    private static void terminal(OptimizerPackTest s) throws Exception {
        ContainerInterfaceTerminal gui = new ContainerInterfaceTerminal(s.player.inventory, terminal);
        java.lang.reflect.Field field = ContainerInterfaceTerminal.class.getDeclaredField("trackedById");
        field.setAccessible(true);
        Map<?, ?> tracked = (Map<?, ?>) field.get(gui);
        for (Map.Entry<?, ?> entry : tracked.entrySet()) {
            java.lang.reflect.Field inv = entry.getValue()
                .getClass()
                .getDeclaredField("patterns");
            inv.setAccessible(true);
            if (inv.get(entry.getValue()) != block.getPatterns()) continue;
            long id = (Long) entry.getKey();
            s.player.inventory.setItemStack(null);
            gui.doAction(s.player, InventoryAction.PICKUP_OR_SET_DOWN, 0, id);
            check(
                s,
                "terminal_extract_whole_holder",
                block.getPatterns()
                    .getStackInSlot(0) == null && IntegratedPattern.count(s.player.inventory.getItemStack()) == 36);
            gui.doAction(s.player, InventoryAction.PICKUP_OR_SET_DOWN, 0, id);
            check(
                s,
                "terminal_insert_whole_holder",
                IntegratedPattern.count(
                    block.getPatterns()
                        .getStackInSlot(0))
                    == 36 && s.player.inventory.getItemStack() == null);
            return;
        }
        throw new AssertionError("Interface terminal did not track the test interface");
    }

    private static void recipe(OptimizerPackTest s) {
        InventoryCrafting matrix = new InventoryCrafting(new ContainerNull(), 3, 3);
        for (int i : new int[] { 0, 2, 6, 8 }) matrix.setInventorySlotContents(i, blank(1));
        for (int i : new int[] { 1, 3, 5, 7 })
            matrix.setInventorySlotContents(i, new ItemStack(GameRegistry.findItem("ae2fc", "fluid_interface")));
        matrix.setInventorySlotContents(
            4,
            net.minecraftforge.oredict.OreDictionary.getOres("circuitElite")
                .get(0)
                .copy());
        boolean found = false;
        for (Object value : CraftingManager.getInstance()
            .getRecipeList()) {
            IRecipe recipe = (IRecipe) value;
            if (IntegratedPattern.is(recipe.getRecipeOutput())) {
                found = recipe.matches(matrix, s.world);
                matrix.setInventorySlotContents(
                    4,
                    net.minecraftforge.oredict.OreDictionary.getOres("circuitData")
                        .get(0)
                        .copy());
                check(s, "recipe_rejects_ev", !recipe.matches(matrix, s.world));
            }
        }
        check(s, "recipe_4_blanks_4_dual_1_iv", found);
    }

    static boolean finish(OptimizerPackTest s) throws Exception {
        IGridProxyable controller = (IGridProxyable) s.world.getTileEntity(40, 80, 8);
        appeng.me.cache.CraftingGridCache cache = (appeng.me.cache.CraftingGridCache) controller.getProxy()
            .getCrafting();
        if (planningPhase == 2) {
            if (--settle > 0) return false;
            planning = cache.beginCraftingJob(
                s.world,
                controller.getProxy()
                    .getGrid(),
                s.source,
                AEItemStack.create(output(33)),
                CraftingMode.STANDARD,
                false,
                job -> {});
            planningPhase = 3;
            return false;
        }
        if (planning == null) {
            check(
                s,
                "all_child_outputs_advertised",
                cache.getCraftingFor(AEItemStack.create(output(33)), null, 0, s.world)
                    .size() >= 1);
            planning = cache.beginCraftingJob(
                s.world,
                controller.getProxy()
                    .getGrid(),
                s.source,
                AEItemStack.create(output(33)),
                CraftingMode.STANDARD,
                false,
                job -> {});
            return false;
        }
        if (!planning.isDone()) return false;
        String expected = planningPhase == 0 ? "CraftingJobV2"
            : planningPhase == 1 ? "CraftingJobFast" : "StrictCraftingJob";
        check(
            s,
            "planner_" + expected,
            planning.get()
                .getClass()
                .getSimpleName()
                .equals(expected)
                && planning.get()
                    .getErrorMessage()
                    .isEmpty());
        appeng.api.storage.data.IItemList<appeng.api.storage.data.IAEStack<?>> bill = AEApi.instance()
            .storage()
            .createAEStackList();
        planning.get()
            .populatePlan(bill);
        check(
            s,
            "expanded_raw_bill_" + expected,
            bill.findPrecise(AEItemStack.create(new ItemStack(Items.iron_ingot))) != null
                && bill.findPrecise(AEItemStack.create(output(33))) != null
                && bill.findPrecise(AEItemStack.create(output(33)))
                    .getCountRequestable() == 1);
        if (planningPhase == 0) {
            planningPhase = 1;
            planning = cache.beginCraftingJob(
                s.world,
                controller.getProxy()
                    .getGrid(),
                s.source,
                AEItemStack.create(output(33)),
                CraftingMode.STANDARD,
                true,
                job -> {});
            return false;
        }
        if (planningPhase == 1) {
            s.world.setBlock(40, 81, 8, dev.aeoptimizer.OrderOptimizer.BLOCK, 0, 3);
            planningPhase = 2;
            settle = 40;
            return false;
        }
        for (IInterfaceHost host : new IInterfaceHost[] { block, dual, part, dualPart }) host.getPatterns()
            .setInventorySlotContents(0, null);
        check(
            s,
            "removal_clears_child_recipes",
            block.getInterfaceDuality().craftingList.isEmpty() && part.getInterfaceDuality().craftingList.isEmpty()
                && dual.getInterfaceDuality().craftingList.isEmpty());
        return true;
    }

    private static ItemStack output(int i) {
        return OptimizerPackTest.token("integrated-" + i);
    }

    private static ItemStack blank(int n) {
        return AEApi.instance()
            .definitions()
            .materials()
            .blankPattern()
            .maybeStack(n)
            .get();
    }

    private static void check(OptimizerPackTest s, String name, boolean ok) {
        OptimizerPackTest.check(ok, name);
        s.row("integrated_" + name, true);
    }
}
