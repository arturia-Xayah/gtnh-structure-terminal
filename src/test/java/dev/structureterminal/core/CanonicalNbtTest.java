package dev.structureterminal.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.lang.reflect.Method;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;

import org.junit.Test;

import dev.structureterminal.bridge.ItemKeys;

public final class CanonicalNbtTest {

    private String canonical(NBTBase tag) throws Exception {
        Method method = ItemKeys.class.getDeclaredMethod("canonical", NBTBase.class);
        method.setAccessible(true);
        return (String) method.invoke(null, tag);
    }

    @Test
    public void compoundInsertionOrderDoesNotChangeSelectionKeys() throws Exception {
        NBTTagCompound a = new NBTTagCompound(), b = new NBTTagCompound();
        a.setInteger("FB", 1);
        a.setInteger("Ea", 2);
        b.setInteger("Ea", 2);
        b.setInteger("FB", 1);
        assertEquals(canonical(a), canonical(b));
    }

    @Test
    public void arraysKeepTheirActualValuesRatherThanOnlyLength() throws Exception {
        assertNotEquals(
            canonical(new NBTTagByteArray(new byte[] { 1, 2 })),
            canonical(new NBTTagByteArray(new byte[] { 1, 3 })));
        assertNotEquals(
            canonical(new NBTTagIntArray(new int[] { 1, 2 })),
            canonical(new NBTTagIntArray(new int[] { 1, 3 })));
    }

    @Test
    public void orderedListsStayOrderedAndOriginalNbtIsNotMutated() throws Exception {
        NBTTagList a = new NBTTagList(), b = new NBTTagList();
        a.appendTag(new NBTTagInt(1));
        a.appendTag(new NBTTagInt(2));
        b.appendTag(new NBTTagInt(2));
        b.appendTag(new NBTTagInt(1));
        NBTBase original = a.copy();
        assertNotEquals(canonical(a), canonical(b));
        assertEquals(original, a);
    }
}
