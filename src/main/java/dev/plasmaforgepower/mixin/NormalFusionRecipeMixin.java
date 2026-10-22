package dev.plasmaforgepower.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.plasmaforgepower.api.FusionPowerAccess;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTRecipe;
import gregtech.common.tileentities.machines.multi.MTEFusionComputer;

@Mixin(targets = "gregtech.common.tileentities.machines.multi.MTEFusionComputer$2", remap = false)
public abstract class NormalFusionRecipeMixin {

    @Shadow
    @Final
    private MTEFusionComputer this$0;

    @Inject(method = "validateRecipe", at = @At("HEAD"), cancellable = true)
    private void pfp$gateFusion(GTRecipe recipe, CallbackInfoReturnable<CheckRecipeResult> cir) {
        CheckRecipeResult failure = ((FusionPowerAccess) this$0)
            .pfp$prepareRecipe((ProcessingLogic) (Object) this, recipe);
        if (failure != null) cir.setReturnValue(failure);
    }

    @Inject(method = "createOverclockCalculator", at = @At("RETURN"))
    private void pfp$capFusionOverclock(GTRecipe recipe,
        CallbackInfoReturnable<gregtech.api.util.OverclockCalculator> cir) {
        long budget = ((FusionPowerAccess) this$0).pfp$attemptPower();
        if (budget >= 0) cir.getReturnValue()
            .setEUt(budget)
            .setAmperage(1);
    }
}
