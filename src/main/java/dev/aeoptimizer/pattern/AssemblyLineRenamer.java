package dev.aeoptimizer.pattern;

import net.minecraft.item.ItemStack;

/** Changes only copies of duplicate, unnamed item inputs. Counts never participate in identity. */
public final class AssemblyLineRenamer {

    public static final String RECIPE_ID = "gt.recipe.fakeAssemblylineProcess";
    public static final String SOURCE_KEY = "aeorderoptimizer:recipeSource";
    // Intentionally independent of client language: a name is part of the AE item identity.
    public static final String NAME_PREFIX = "重命名";

    private AssemblyLineRenamer() {}

    public static ItemStack[] rename(ItemStack[] inputs) {
        ItemStack[] result = inputs.clone();
        for (int i = 0; i < inputs.length; i++) {
            ItemStack input = inputs[i];
            if (input == null || input.stackSize <= 0 || input.hasDisplayName()) continue;
            boolean duplicate = false;
            for (int j = 0; j < i; j++) {
                if (inputs[j] != null && inputs[j].stackSize > 0 && same(input, inputs[j])) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) continue;
            ItemStack renamed = input.copy();
            int suffix = 1;
            do {
                // Exactly the vanilla operation used by GT's FormingPressBackend.
                renamed.setStackDisplayName(NAME_PREFIX + suffix++);
            } while (contains(result, renamed));
            result[i] = renamed;
        }
        return result;
    }

    private static boolean contains(ItemStack[] stacks, ItemStack candidate) {
        for (ItemStack stack : stacks) if (same(stack, candidate)) return true;
        return false;
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return a != null && b != null && a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b);
    }
}
