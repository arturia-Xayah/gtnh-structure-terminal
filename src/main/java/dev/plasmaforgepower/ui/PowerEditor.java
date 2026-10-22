package dev.plasmaforgepower.ui;

import java.text.NumberFormat;
import java.util.Locale;

import dev.plasmaforgepower.core.PowerLimit;
import dev.plasmaforgepower.core.PowerSettings;

/** Per-open-window drafts; editing a field never changes an in-flight recipe or the server configuration. */
public final class PowerEditor {

    public PowerSettings saved = new PowerSettings();
    public int selected = 1;
    public long ratedPower;
    public long ratedVoltage;
    public boolean formed;
    public String message = "next_recipe";
    public int changes;
    public boolean pending;
    private final String[] amps = new String[17];
    private final int[] tiers = new int[17];
    private final int[] generation = new int[17];
    private final boolean[] dirty = new boolean[17];
    private int[] sentGeneration;

    public PowerEditor() {
        refreshCleanFields();
    }

    public String amps() {
        return amps[selected + 1];
    }

    public int tier() {
        return tiers[selected + 1];
    }

    public String amps(int color) {
        return amps[color + 1];
    }

    public int tier(int color) {
        return tiers[color + 1];
    }

    public boolean dirty(int color) {
        return dirty[color + 1];
    }

    public void setAmps(String value) {
        if (!pending && !value.equals(amps())) {
            amps[selected + 1] = value;
            edited();
        }
    }

    public void setTier(int value) {
        if (!pending && value != tier()) {
            tiers[selected + 1] = value;
            edited();
        }
    }

    private void edited() {
        dirty[selected + 1] = true;
        generation[selected + 1]++;
        message = "unsaved";
    }

    public int dirtyCount() {
        int count = 0;
        for (boolean value : dirty) if (value) count++;
        return count;
    }

    public boolean isAutomatic() {
        try {
            return draft().isAuto();
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    public PowerLimit draft() {
        return dirty(selected) ? PowerLimit.parse(amps(), tier()) : saved.get(selected);
    }

    public long previewPower() {
        try {
            PowerLimit limit = draft();
            return limit.isAuto() ? (formed ? ratedPower : -1) : limit.eut();
        } catch (IllegalArgumentException invalid) {
            return -1;
        }
    }

    public String previewText() {
        long power = previewPower();
        return power < 0 ? "—" : format(power) + " EU/t";
    }

    public boolean overBudget() {
        return formed && previewPower() > ratedPower;
    }

    public boolean valid() {
        try {
            for (int color = -1; color < 16; color++) if (dirty(color)) PowerLimit.parse(amps(color), tier(color));
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    public void beginSave() {
        pending = true;
        sentGeneration = generation.clone();
    }

    public void snapshot(PowerSettings settings, long power, long voltage, boolean formed, boolean accepted) {
        if (accepted && sentGeneration != null) {
            for (int i = 0; i < dirty.length; i++) if (sentGeneration[i] == generation[i]) dirty[i] = false;
        }
        this.saved = settings.copy();
        this.ratedPower = power;
        this.ratedVoltage = voltage;
        this.formed = formed;
        refreshCleanFields();
    }

    private void refreshCleanFields() {
        for (int color = -1; color < 16; color++) if (!dirty[color + 1]) {
            PowerLimit limit = saved.get(color);
            amps[color + 1] = limit.amps();
            tiers[color + 1] = limit.tier();
        }
    }

    public static String format(long power) {
        return NumberFormat.getIntegerInstance(Locale.US)
            .format(power);
    }
}
