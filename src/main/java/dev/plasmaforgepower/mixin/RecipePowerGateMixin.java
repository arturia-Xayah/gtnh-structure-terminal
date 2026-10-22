package dev.plasmaforgepower.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.plasmaforgepower.api.ForgePowerAccess;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.common.tileentities.machines.multi.MTEPlasmaForge;

/** Reject an over-budget base recipe before catalyst adjustment, overclocking or ingredient consumption. */
@Mixin(targets = "gregtech.common.tileentities.machines.multi.MTEPlasmaForge$2", remap = false)
public abstract class RecipePowerGateMixin {

    @Shadow
    @Final
    private MTEPlasmaForge this$0;

    @Inject(method = "validateRecipe", at = @At("HEAD"), cancellable = true)
    private void pfp$gate(GTRecipe recipe, CallbackInfoReturnable<CheckRecipeResult> cir) {
        long limit = ((ForgePowerAccess) this$0).pfp$attemptPower();
        if (limit >= 0 && recipe.mEUt > limit) {
            cir.setReturnValue(CheckRecipeResultRegistry.insufficientPower(recipe.mEUt));
        } else if (!((ForgePowerAccess) this$0).pfp$sourceAllowed(recipe)) {
            cir.setReturnValue(CheckRecipeResultRegistry.NO_RECIPE);
        }
    }
}
