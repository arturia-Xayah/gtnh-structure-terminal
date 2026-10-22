package dev.structureterminal.ae;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

/** Read-only lookup against the terminal's unfiltered, synchronized inventory. */
public final class CraftableInputs {

    private CraftableInputs() {}

    public static boolean isCraftable(boolean powered, IItemList<IAEStack<?>> inventory, IAEStack<?> input) {
        if (!powered || inventory == null || input == null) return false;
        IAEStack<?> entry = inventory.findPrecise(input);
        return entry != null && entry.isCraftable();
    }
}
