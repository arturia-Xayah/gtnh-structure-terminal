package dev.plasmaforgepower.api;

import net.minecraft.nbt.NBTTagCompound;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTRecipe;

public interface FusionPowerAccess extends ForgePowerAccess {

    void pfp$inputGroup(int color, boolean dual);

    void pfp$finishAttempt(boolean success);

    CheckRecipeResult pfp$prepareRecipe(ProcessingLogic logic, GTRecipe recipe);

    NBTTagCompound pfp$saveFusionState();

    void pfp$loadFusionState(NBTTagCompound tag);
}
