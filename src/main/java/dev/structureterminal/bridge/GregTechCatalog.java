package dev.structureterminal.bridge;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

/** Optional, runtime registry enumeration; never substitutes GTO IDs for installed GTNH machines. */
final class GregTechCatalog {

    private static List<ItemStack> cached;

    private GregTechCatalog() {}

    static synchronized List<ItemStack> items() {
        if (cached != null) return cached;
        List<ItemStack> result = new ArrayList<ItemStack>();
        try {
            Object entries = Class.forName("gregtech.api.GregTechAPI")
                .getField("METATILEENTITIES")
                .get(null);
            for (int i = 1; i < Array.getLength(entries); i++) {
                Object machine = Array.get(entries, i);
                if (machine == null) continue;
                try {
                    Object value = machine.getClass()
                        .getMethod("getStackForm", long.class)
                        .invoke(machine, 1L);
                    if (value instanceof ItemStack) {
                        ItemStack stack = ((ItemStack) value).copy();
                        stack.stackSize = 1;
                        result.add(stack);
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {}
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        cached = Collections.unmodifiableList(result);
        return cached;
    }
}
