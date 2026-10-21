package dev.aeoptimizer.mixin;

import java.util.concurrent.Future;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.config.CraftingMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.me.cache.CraftingGridCache;
import dev.aeoptimizer.network.OptimizerNetworks;
import dev.aeoptimizer.network.TileOptimizer;
import dev.aeoptimizer.planning.StrictCraftingJob;

@Mixin(value = CraftingGridCache.class, remap = false)
public abstract class CraftingGridMixin implements dev.aeoptimizer.api.PatternCatalog {

    @org.spongepowered.asm.mixin.Shadow
    protected java.util.Map<ICraftingPatternDetails, java.util.List<appeng.api.networking.crafting.ICraftingMedium>> craftingMethods;

    public java.util.List<ICraftingPatternDetails> aeo$registeredPatterns() {
        return new java.util.ArrayList<>(craftingMethods.keySet());
    }

    @Inject(
        method = "beginCraftingJob(Lnet/minecraft/world/World;Lappeng/api/networking/IGrid;Lappeng/api/networking/security/BaseActionSource;Lappeng/api/storage/data/IAEStack;Lappeng/api/config/CraftingMode;ZLappeng/api/networking/crafting/ICraftingCallback;)Ljava/util/concurrent/Future;",
        at = @At("HEAD"),
        cancellable = true)
    private void aeo$begin(World world, IGrid orderingGrid, BaseActionSource source, IAEStack stack, CraftingMode mode,
        boolean lite, ICraftingCallback callback, CallbackInfoReturnable<Future<ICraftingJob>> cir) {
        if (world == null || orderingGrid == null || source == null || stack == null) return;
        TileOptimizer optimizer = OptimizerNetworks.find(orderingGrid);
        if (optimizer != null) cir.setReturnValue(
            new StrictCraftingJob(world, orderingGrid, source, stack, mode, callback, optimizer.detailed).schedule());
    }
}
