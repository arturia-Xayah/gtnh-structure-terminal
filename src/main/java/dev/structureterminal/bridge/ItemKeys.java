package dev.structureterminal.bridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;

import dev.structureterminal.core.Fingerprints;

public final class ItemKeys {

    private ItemKeys() {}

    public static String key(ItemStack stack) {
        return Item.itemRegistry.getNameForObject(stack.getItem()) + "@"
            + stack.getItemDamage()
            + (stack.hasTagCompound() ? ":" + digest(canonical(stack.getTagCompound())) : "");
    }

    public static boolean same(ItemStack a, ItemStack b) {
        return a != null && b != null
            && a.getItem() == b.getItem()
            && a.getItemDamage() == b.getItemDamage()
            && ItemStack.areItemStackTagsEqual(a, b);
    }

    public static String group(List<ItemStack> stacks) {
        List<String> keys = new ArrayList<String>();
        for (ItemStack s : stacks) keys.add(key(s));
        return Fingerprints.group(keys);
    }

    public static String digest(String text) {
        return Fingerprints.digest(text);
    }

    private static String canonical(NBTBase tag) {
        StringBuilder out = new StringBuilder().append(tag.getId())
            .append(':');
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            List<String> keys = new ArrayList<String>();
            for (Object key : compound.func_150296_c()) keys.add((String) key);
            Collections.sort(keys);
            for (String key : keys) {
                String value = canonical(compound.getTag(key));
                out.append(key.length())
                    .append(':')
                    .append(key)
                    .append(value.length())
                    .append(':')
                    .append(value);
            }
        } else if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag.copy();
            for (int i = list.tagCount() - 1; i >= 0; i--) {
                String value = canonical(list.removeTag(i));
                out.append(value.length())
                    .append(':')
                    .append(value);
            }
        } else if (tag instanceof NBTTagByteArray)
            out.append(java.util.Arrays.toString(((NBTTagByteArray) tag).func_150292_c()));
        else if (tag instanceof NBTTagIntArray)
            out.append(java.util.Arrays.toString(((NBTTagIntArray) tag).func_150302_c()));
        else out.append(tag.toString());
        return out.toString();
    }
}
