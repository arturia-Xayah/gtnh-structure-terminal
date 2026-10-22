package dev.plasmaforgepower.api;

import dev.plasmaforgepower.core.PowerSettings;
import gregtech.api.util.GTRecipe;

/** Added to the existing forge and fusion controllers; no new blocks or inventory items are registered. */
public interface ForgePowerAccess {

    PowerSettings pfp$getSettings();

    int pfp$applySettings(PowerSettings settings);

    long pfp$ratedPower();

    long pfp$ratedVoltage();

    boolean pfp$isFormed();

    int pfp$activeColor();

    long pfp$attemptPower();

    boolean pfp$sourceAllowed(GTRecipe recipe);
}
