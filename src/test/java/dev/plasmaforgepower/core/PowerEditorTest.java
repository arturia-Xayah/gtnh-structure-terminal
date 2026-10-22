package dev.plasmaforgepower.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import dev.plasmaforgepower.ui.PowerEditor;

public class PowerEditorTest {

    @Test
    public void typingNeverMutatesSavedConfiguration() {
        PowerEditor editor = new PowerEditor();
        editor.setAmps("1");
        editor.setTier(0);
        assertTrue(
            editor.saved.get(1)
                .isAuto());
        assertEquals(8388608L, editor.previewPower());
        assertEquals(1, editor.dirtyCount());
    }

    @Test
    public void serverSnapshotsPreserveUnsavedOtherColors() {
        PowerEditor editor = new PowerEditor();
        editor.setAmps("1");
        PowerSettings server = new PowerSettings();
        server.set(2, PowerLimit.parse("16", 2));
        editor.snapshot(server, 137438953472L, PowerLimit.voltage(2), true, false);
        assertEquals("1", editor.amps());
        editor.selected = 2;
        assertEquals("16", editor.amps());
    }

    @Test
    public void saveAcknowledgmentRefreshesCorrectedValues() {
        PowerEditor editor = new PowerEditor();
        editor.setAmps("16");
        editor.beginSave();
        PowerSettings server = new PowerSettings();
        server.set(1, PowerLimit.parse("4", 2));
        editor.snapshot(server, 4 * PowerLimit.voltage(2), PowerLimit.voltage(2), true, true);
        assertEquals("4", editor.amps());
        assertEquals(0, editor.dirtyCount());
    }

    @Test
    public void incompleteStructureHasNoInventedSupply() {
        PowerEditor editor = new PowerEditor();
        assertEquals(-1, editor.previewPower());
        editor.setAmps("16");
        assertEquals(16 * PowerLimit.voltage(2), editor.previewPower());
        assertFalse(editor.overBudget());
    }

    @Test
    public void invalidDraftIsVisibleAndCannotBeSaved() {
        PowerEditor editor = new PowerEditor();
        editor.setAmps("-2");
        assertFalse(editor.valid());
        assertEquals(-1, editor.previewPower());
        editor.setAmps("0");
        assertTrue(editor.valid());
        assertTrue(editor.isAutomatic());
    }
}
