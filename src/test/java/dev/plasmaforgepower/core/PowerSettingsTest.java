package dev.plasmaforgepower.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Random;

import org.junit.Test;

public class PowerSettingsTest {

    @Test
    public void exactVoltageUnitsIncludingMax() {
        long[] expected = { 8388608L, 33554432L, 134217728L, 536870912L, 2147483640L };
        for (int i = 0; i < expected.length; i++) assertEquals(
            expected[i],
            PowerLimit.parse("1", i)
                .eut());
        assertEquals(
            137438953472L,
            PowerLimit.parse("1024", 2)
                .eut());
    }

    @Test
    public void newFusionVoltageUnitsKeepOldIdsAndSortByVoltage() {
        long previous = 0;
        for (int tier : PowerLimit.displayOrder()) {
            long voltage = PowerLimit.voltage(tier);
            assertTrue(voltage > previous);
            assertEquals(tier, PowerLimit.displayTier(voltage));
            assertEquals(
                voltage,
                PowerLimit.parse("1", tier)
                    .eut());
            previous = voltage;
        }
        assertEquals("UEV", PowerLimit.TIERS[0]);
        assertEquals("UMV", PowerLimit.TIERS[2]);
        assertEquals("LuV", PowerLimit.TIERS[11]);
        assertEquals(
            32768L,
            PowerLimit.parse("1", 11)
                .eut());
    }

    @Test
    public void automaticSentinelsNormalize() {
        assertTrue(
            PowerLimit.parse("0", 0)
                .isAuto());
        assertTrue(
            PowerLimit.parse("-1", 4)
                .isAuto());
        assertEquals(
            "-1",
            PowerLimit.parse("0.0", 2)
                .amps());
    }

    @Test
    public void invalidAndOverflowInputCannotBecomeUnlimited() {
        for (String amps : new String[] { "", "-2", "NaN", "Infinity", "1e12", "1/0", "-0.1", "0.0000000000000001",
            "99999999999999999999999999999" }) {
            try {
                PowerLimit.parse(amps, 2);
                fail(amps);
            } catch (IllegalArgumentException expected) {}
        }
        for (int tier : new int[] { -1, PowerLimit.TIERS.length, 99 }) {
            try {
                PowerLimit.parse("0", tier);
                fail();
            } catch (IllegalArgumentException expected) {}
        }
    }

    @Test
    public void fractionalAmpsRemainConservativeAndExactEUtIsRetained() {
        assertEquals(
            134217728L,
            PowerLimit.parse("0.25", 3)
                .eut());
        PowerLimit limit = PowerLimit.ofEUt(123456789L, 2);
        assertTrue(
            PowerLimit.parse(limit.amps(), limit.tier())
                .eut() <= limit.eut());
        assertTrue(
            limit.eut() - PowerLimit.parse(limit.amps(), limit.tier())
                .eut() <= 1);
    }

    @Test
    public void colorsAreIndependentAndCopiesAreNotAliased() {
        PowerSettings original = new PowerSettings();
        original.set(1, PowerLimit.parse("1", 0));
        original.set(2, PowerLimit.parse("16", 2));
        PowerSettings copy = original.copy();
        copy.set(1, PowerLimit.parse("2", 2));
        assertEquals(PowerLimit.parse("1", 0), original.get(1));
        assertTrue(
            original.get(-1)
                .isAuto());
        assertTrue(
            original.get(0)
                .isAuto());
        assertEquals(PowerLimit.parse("16", 2), copy.get(2));
    }

    @Test
    public void lowerSupplyPermanentlyClampsButAutomaticStillFollowsSupply() {
        PowerSettings settings = new PowerSettings();
        settings.set(1, PowerLimit.parse("16", 2));
        assertEquals(1, settings.clamp(4 * PowerLimit.voltage(2), PowerLimit.voltage(2)));
        assertEquals(
            "4",
            settings.get(1)
                .amps());
        assertEquals(0, settings.clamp(1024 * PowerLimit.voltage(2), PowerLimit.voltage(2)));
        assertEquals(
            "4",
            settings.get(1)
                .amps());
        assertTrue(
            settings.get(-1)
                .isAuto());
    }

    @Test
    public void incompleteOrAbsentSupplyNeverErasesConfiguration() {
        PowerSettings settings = new PowerSettings();
        settings.set(2, PowerLimit.parse("16", 2));
        PowerSettings before = settings.copy();
        assertEquals(0, settings.clamp(0, 0));
        assertEquals(before, settings);
    }

    @Test
    public void belowUevSupplyCanStillBeRepresentedExactly() {
        PowerSettings settings = new PowerSettings();
        settings.set(1, PowerLimit.parse("1", 0));
        assertEquals(1, settings.clamp(2048, 2048));
        assertEquals(
            2048,
            settings.get(1)
                .eut());
        assertEquals(
            "1",
            settings.get(1)
                .amps());
    }

    @Test
    public void randomizedClampNeverExceedsSupplyOrChangesAutomaticGroups() {
        Random random = new Random(76543);
        for (int i = 0; i < 5000; i++) {
            long supply = 1 + (random.nextLong() & Long.MAX_VALUE) / 1024;
            PowerSettings settings = new PowerSettings();
            long request = 1 + (random.nextLong() & Long.MAX_VALUE) / 256;
            settings.set(1, PowerLimit.ofEUt(request, i % PowerLimit.TIERS.length));
            settings.clamp(supply, PowerLimit.voltage(i % PowerLimit.TIERS.length));
            assertEquals(
                Math.min(request, supply),
                settings.get(1)
                    .eut());
            assertTrue(
                settings.get(-1)
                    .isAuto());
            assertEquals(
                supply,
                settings.get(-1)
                    .effective(supply));
        }
    }

    @Test
    public void invalidDyeIdsAreRejected() {
        for (int color : new int[] { -2, 16, Integer.MAX_VALUE }) {
            try {
                new PowerSettings().get(color);
                fail();
            } catch (IllegalArgumentException expected) {}
        }
        assertFalse(PowerSettings.validColor(16));
        assertTrue(new PowerSettings().isAllAuto());
    }
}
