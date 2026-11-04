package dev.aeoptimizer.core;

import static org.junit.Assert.*;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import org.junit.Test;

import dev.aeoptimizer.pattern.AssemblyLineRenamer;

public class AssemblyLineRenamerTest {

    @Test
    public void threeStacksKeepFirstAndRenameCopiesWithoutChangingAmounts() {
        Item item = new Item();
        ItemStack[] in = { new ItemStack(item, 64), new ItemStack(item, 32), new ItemStack(item, 10000) };
        ItemStack[] out = AssemblyLineRenamer.rename(in);
        assertSame(in[0], out[0]);
        assertEquals("重命名1", out[1].getDisplayName());
        assertEquals("重命名2", out[2].getDisplayName());
        for (int i = 0; i < in.length; i++) {
            assertFalse(in[i].hasDisplayName());
            assertEquals(in[i].stackSize, out[i].stackSize);
        }
    }

    @Test
    public void interleavedItemsNumberIndependently() {
        Item a = new Item(), b = new Item();
        ItemStack[] out = AssemblyLineRenamer.rename(
            new ItemStack[] { new ItemStack(a), new ItemStack(b), new ItemStack(a), new ItemStack(b),
                new ItemStack(a) });
        assertEquals("重命名1", out[2].getDisplayName());
        assertEquals("重命名1", out[3].getDisplayName());
        assertEquals("重命名2", out[4].getDisplayName());
    }

    @Test
    public void metadataNbtNullAndZeroCountAreNotFalseDuplicates() {
        Item item = new Item();
        ItemStack tagged = new ItemStack(item);
        tagged.setTagCompound(new NBTTagCompound());
        tagged.getTagCompound()
            .setInteger("payload", 7);
        ItemStack[] in = { null, new ItemStack(item, 0), new ItemStack(item, 4, 1), tagged, new ItemStack(item) };
        ItemStack[] out = AssemblyLineRenamer.rename(in);
        for (int i = 0; i < in.length; i++) assertSame(in[i], out[i]);
    }

    @Test
    public void existingNamesArePreservedAndReserved() {
        Item item = new Item();
        ItemStack named = new ItemStack(item).setStackDisplayName("重命名1");
        ItemStack[] in = { new ItemStack(item), named, new ItemStack(item), named.copy() };
        ItemStack[] out = AssemblyLineRenamer.rename(in);
        assertSame(named, out[1]);
        assertEquals("重命名2", out[2].getDisplayName());
        assertSame(in[3], out[3]);
    }

    @Test
    public void nameMatchesVanillaOperationAndRetainsFullOriginalNbt() {
        ItemStack original = new ItemStack(new Item(), 64, 8);
        NBTTagCompound tag = new NBTTagCompound(), display = new NBTTagCompound();
        NBTTagList lore = new NBTTagList();
        lore.appendTag(new NBTTagString("Keep this lore"));
        display.setTag("Lore", lore);
        tag.setTag("display", display);
        tag.setInteger("payload", 42);
        original.setTagCompound(tag);
        ItemStack out = AssemblyLineRenamer.rename(new ItemStack[] { original, original })[1];
        ItemStack expected = original.copy()
            .setStackDisplayName("重命名1");
        assertTrue(ItemStack.areItemStacksEqual(expected, out));
        assertFalse(original.hasDisplayName());
        assertEquals(
            42,
            out.getTagCompound()
                .getInteger("payload"));
        assertEquals(
            1,
            original.getTagCompound()
                .getCompoundTag("display")
                .getTagList("Lore", 8)
                .tagCount());
    }

    @Test
    public void repeatedApplicationDoesNotRenameAgain() {
        ItemStack a = new ItemStack(new Item());
        ItemStack[] once = AssemblyLineRenamer.rename(new ItemStack[] { a, a, a });
        ItemStack[] twice = AssemblyLineRenamer.rename(once);
        for (int i = 0; i < once.length; i++) assertSame(once[i], twice[i]);
    }
}
