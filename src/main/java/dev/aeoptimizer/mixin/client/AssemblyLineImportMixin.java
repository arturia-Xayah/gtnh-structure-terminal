package dev.aeoptimizer.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.vfyjxf.nee.nei.NEEPatternTerminalHandler;
import com.github.vfyjxf.nee.network.packet.PacketNEIPatternRecipe;

import codechicken.nei.recipe.IRecipeHandler;
import dev.aeoptimizer.mixin.NEIPatternPacketAccessor;
import dev.aeoptimizer.pattern.AssemblyLineRenamer;

/** Carries recipe provenance only; the server decides whether the terminal's network opted in. */
@Mixin(value = NEEPatternTerminalHandler.class, remap = false)
public abstract class AssemblyLineImportMixin {

    @Inject(method = "packProcessRecipe", at = @At("RETURN"))
    private void aeo$markAssemblyLine(IRecipeHandler recipe, int recipeIndex, int multiplier,
        CallbackInfoReturnable<PacketNEIPatternRecipe> cir) {
        if (!AssemblyLineRenamer.RECIPE_ID.equals(recipe.getOverlayIdentifier())) return;
        PacketNEIPatternRecipe packet = cir.getReturnValue();
        if (packet == null) return;
        ((NEIPatternPacketAccessor) packet).aeo$getInput()
            .setString(AssemblyLineRenamer.SOURCE_KEY, AssemblyLineRenamer.RECIPE_ID);
    }
}
