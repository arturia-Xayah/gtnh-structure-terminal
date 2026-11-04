package dev.aeoptimizer.mixin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.tile.inventory.AppEngInternalInventory;
import dev.structureterminal.pattern.IntegratedPattern;

/** Expands only at native recipe refresh. Dispatch still receives the original child details and original host. */
@Mixin(value = DualityInterface.class, remap = false)
public abstract class IntegratedPatternDualityMixin {

    @Shadow
    @Final
    private AppEngInternalInventory patterns;
    @Shadow
    @Final
    private IInterfaceHost iHost;
    @Shadow
    public List<ICraftingPatternDetails> craftingList;

    @Shadow
    public abstract int getPriority();

    @Unique
    private final Map<Integer, ItemStack> modernization$bundles = new HashMap<>();
    @Unique
    private final Map<Integer, List<ICraftingPatternDetails>> modernization$children = new HashMap<>();

    @Inject(method = "updateCraftingList", at = @At("HEAD"))
    private void modernization$prune(CallbackInfo ci) {
        modernization$bundles.keySet()
            .removeIf(slot -> !IntegratedPattern.is(patterns.getStackInSlot(slot)));
        modernization$children.keySet()
            .retainAll(modernization$bundles.keySet());
        // The native identity-based refresh removes child details (they are not physical parent stacks)
        // before invoking addToCraftingList, so no duplicate advertisement or second grid event is needed.
    }

    @Inject(method = "addToCraftingList", at = @At("HEAD"), cancellable = true)
    private void modernization$expand(int slot, CallbackInfo ci) {
        ItemStack holder = patterns.getStackInSlot(slot);
        if (!IntegratedPattern.is(holder)) return;
        ci.cancel();
        if (!ItemStack.areItemStacksEqual(holder, modernization$bundles.get(slot))) {
            List<ICraftingPatternDetails> children = new ArrayList<>();
            for (ItemStack child : IntegratedPattern.read(holder)) {
                ICraftingPatternDetails details = IntegratedPattern.details(
                    child,
                    iHost.getTileEntity()
                        .getWorldObj());
                if (details != null) children.add(details);
            }
            modernization$bundles.put(slot, holder.copy());
            modernization$children.put(slot, children);
        }
        List<ICraftingPatternDetails> children = modernization$children.get(slot);
        if (children == null || children.isEmpty()) return;
        if (craftingList == null) craftingList = new ArrayList<>();
        for (ICraftingPatternDetails details : children) {
            details.setPriority(slot - 36 * getPriority());
            craftingList.add(details);
        }
    }
}
