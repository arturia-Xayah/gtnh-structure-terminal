package dev.structureterminal.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

import dev.structureterminal.TerminalSettings;

public final class SettingsTest {

    @Test
    public void selectingTilePartEnablesNativeHatchesAndKeepsExplicitChannel() {
        net.minecraft.block.Block hatch = new net.minecraft.block.Block(net.minecraft.block.material.Material.iron) {

            @Override
            public boolean hasTileEntity(int meta) {
                return true;
            }
        };
        net.minecraft.item.ItemStack selected = new net.minecraft.item.ItemStack(
            new net.minecraft.item.ItemBlock(hatch));
        TerminalSettings settings = new TerminalSettings();
        settings.enableHatchesForSelection(selected);
        assertEquals(Integer.valueOf(1), settings.channels.get("gt_hatch"));
        settings.channels.put("gt_hatch", 7);
        settings.enableHatchesForSelection(selected);
        assertEquals(Integer.valueOf(7), settings.channels.get("gt_hatch"));
    }

    @Test
    public void allSelectionsSurviveRoundTrip() {
        TerminalSettings s = new TerminalSettings();
        s.coilTier = 14;
        s.repeatCount = 99;
        s.noHatches = false;
        s.keepHatches = false;
        s.replaceCoils = true;
        s.size = 17;
        s.mirror = true;
        s.network = true;
        s.channels.put("glass", 8);
        s.channels.put("coil", 3);
        s.choices.put(Fingerprints.digest("position"), "gregtech:gt.blockmachines@123");
        TerminalSettings copy = TerminalSettings.read(s.write());
        assertEquals(17, copy.size);
        assertEquals(14, copy.coilTier);
        assertEquals(99, copy.repeatCount);
        assertEquals(false, copy.noHatches);
        assertEquals(false, copy.keepHatches);
        assertTrue(copy.replaceCoils);
        assertTrue(copy.mirror);
        assertTrue(copy.network);
        assertEquals(s.channels, copy.channels);
        assertEquals(s.choices, copy.choices);
    }

    @Test
    public void operationPersistsAndLegacyCoilModeMigrates() {
        for (int mode = TerminalSettings.BUILD; mode <= TerminalSettings.DISMANTLE; mode++) {
            TerminalSettings settings = new TerminalSettings();
            settings.operation = mode;
            assertEquals(mode, TerminalSettings.read(settings.write()).operation);
        }
        NBTTagCompound old = new NBTTagCompound();
        old.setInteger("ReplaceCoilMode", 1);
        TerminalSettings migrated = TerminalSettings.read(old);
        assertEquals(TerminalSettings.REPLACE, migrated.operation);
        assertTrue(migrated.replaceCoils);
        migrated.operation = TerminalSettings.DISMANTLE;
        assertEquals(false, migrated.replacing());
    }

    @Test
    public void malformedAndExcessiveValuesAreRejectedBeforeMachineCalls() {
        for (int size : new int[] { 0, -1, 100, Integer.MAX_VALUE }) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("size", size);
            try {
                TerminalSettings.read(tag);
                fail("Accepted size " + size);
            } catch (IllegalArgumentException expected) {}
        }
        for (String key : new String[] { "CoilTier", "RepeatCount", "Operation" }) {
            for (int value : new int[] { -1, 100, Integer.MAX_VALUE }) {
                NBTTagCompound invalid = new NBTTagCompound();
                invalid.setInteger(key, value);
                try {
                    TerminalSettings.read(invalid);
                    fail("Accepted " + key + "=" + value);
                } catch (IllegalArgumentException expected) {}
            }
        }
        assertTrue(TerminalSettings.read(new NBTTagCompound()).noHatches);
        assertTrue(TerminalSettings.read(new NBTTagCompound()).keepHatches);
        NBTTagCompound tag = new NBTTagCompound(), channels = new NBTTagCompound();
        channels.setInteger("glass", Integer.MAX_VALUE);
        tag.setTag("channels", channels);
        try {
            TerminalSettings.read(tag);
            fail("Accepted excessive channel");
        } catch (IllegalArgumentException expected) {}
    }
}
