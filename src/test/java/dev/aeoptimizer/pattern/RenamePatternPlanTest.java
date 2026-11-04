package dev.aeoptimizer.pattern;

import static org.junit.Assert.*;

import java.util.Arrays;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.Test;

public class RenamePatternPlanTest {

    private final ItemStack blank = new ItemStack(new Item());
    private final ItemStack a = new ItemStack(new Item()), b = new ItemStack(new Item());

    @Test
    public void terminalThenInventoryThenNetworkWithoutChangingInputs() {
        ItemStack terminal = blank.copy(), carried = blank.copy();
        ItemStack[] inventory = { carried, null, null };
        RenamePatternEncoder.BlankPlan plan = RenamePatternEncoder
            .plan(inventory, terminal, blank, Arrays.asList(a, b));
        assertNotNull(plan);
        assertEquals(0, plan.networkNeeded);
        assertNull(plan.terminalBlank);
        assertEquals(1, terminal.stackSize);
        assertEquals(1, carried.stackSize);
        assertSame(carried, inventory[0]);
        assertSame(a.getItem(), plan.inventory[0].getItem());
        assertSame(b.getItem(), plan.inventory[1].getItem());
    }

    @Test
    public void freesBlankSlotBeforeCheckingOutputSpace() {
        ItemStack[] inventory = { blank.copy(), null };
        inventory[0].stackSize = 2;
        RenamePatternEncoder.BlankPlan plan = RenamePatternEncoder.plan(inventory, null, blank, Arrays.asList(a, b));
        assertNotNull(plan);
        assertEquals(0, plan.networkNeeded);
        assertEquals(2, inventory[0].stackSize);
    }

    @Test
    public void insufficientSpaceMakesNoPartialChanges() {
        ItemStack[] inventory = { blank.copy() };
        inventory[0].stackSize = 2;
        assertNull(RenamePatternEncoder.plan(inventory, null, blank, Arrays.asList(a, b)));
        assertEquals(2, inventory[0].stackSize);
    }

    @Test
    public void networkShortfallIsExact() {
        ItemStack[] inventory = { null, null };
        RenamePatternEncoder.BlankPlan plan = RenamePatternEncoder.plan(inventory, null, blank, Arrays.asList(a, b));
        assertEquals(2, plan.networkNeeded);
        assertNull(inventory[0]);
        assertNull(inventory[1]);
    }

    @Test
    public void unrelatedOrNamedItemsAreNotConsumed() {
        ItemStack named = blank.copy()
            .setStackDisplayName("keep");
        ItemStack[] inventory = { named, null, null };
        RenamePatternEncoder.BlankPlan plan = RenamePatternEncoder.plan(inventory, a, blank, Arrays.asList(a, b));
        assertEquals(2, plan.networkNeeded);
        assertTrue(ItemStack.areItemStacksEqual(named, plan.inventory[0]));
        assertTrue(ItemStack.areItemStacksEqual(a, plan.terminalBlank));
    }

    @Test
    public void retainsUnusedBlankQuantity() {
        ItemStack terminal = blank.copy();
        terminal.stackSize = 12;
        RenamePatternEncoder.BlankPlan plan = RenamePatternEncoder
            .plan(new ItemStack[4], terminal, blank, Arrays.asList(a, b));
        assertEquals(10, plan.terminalBlank.stackSize);
        assertEquals(12, terminal.stackSize);
        assertNotSame(a, plan.inventory[0]);
    }
}
