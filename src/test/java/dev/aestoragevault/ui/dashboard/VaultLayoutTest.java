package dev.aestoragevault.ui.dashboard;

import static org.junit.Assert.*;

import org.junit.Test;

public class VaultLayoutTest {

    @Test
    public void bothLayoutsFitSupportedScaledViewportsAndKeepAllSlotsAwayFromControls() {
        for (int[] viewport : new int[][] { { 320, 240 }, { 427, 240 }, { 352, 300 }, { 640, 480 } }) {
            VaultLayout l = VaultLayout.fit(viewport[0], viewport[1]);
            assertTrue(l.width <= viewport[0] - 16 && l.height <= viewport[1] - 16);
            for (int i = 0; i < 36; i++) {
                assertTrue(l.slotX(i) >= 1 && l.slotX(i) + 17 < l.statusX);
                assertTrue(l.slotY(i) > l.panelY + l.panelHeight);
                assertTrue(l.slotY(i) + 17 < l.height);
                for (int j = i + 1; j < 36; j++) assertTrue(l.slotX(i) != l.slotX(j) || l.slotY(i) != l.slotY(j));
            }
            assertTrue(l.controlsY + 18 < l.height);
            assertTrue(l.automaticY + 18 <= l.controlsY);
        }
    }

    @Test
    public void compactModeIsChosenByEitherLimitedDimension() {
        assertTrue(VaultLayout.fit(320, 1000).compact);
        assertTrue(VaultLayout.fit(1000, 240).compact);
        assertFalse(VaultLayout.fit(352, 300).compact);
    }
}
