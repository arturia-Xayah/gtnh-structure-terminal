package dev.aestoragevault.ui.dashboard;

import static org.junit.Assert.*;

import org.junit.Test;

public class ContributionLayoutTest {

    @Test
    public void allSixRowsAndButtonsFitSupportedGuiScales() {
        for (int height : new int[] { 240, 270, 281, 282, 360, 1080 }) {
            ContributionLayout layout = ContributionLayout.fit(height);
            assertTrue(layout.height <= height - 16);
            assertTrue(layout.controlsY + 18 < layout.height);
            for (int row = 0; row < 6; row++) {
                assertTrue(layout.rowY(row) >= 52);
                assertTrue(layout.rowY(row) + layout.rowHeight - 2 < layout.controlsY);
                // Both text baselines and the full native 16px item icon fit inside the row.
                assertTrue(layout.rowHeight - 2 >= 22);
                if (row > 0) assertTrue(layout.rowY(row - 1) + layout.rowHeight == layout.rowY(row));
            }
        }
    }

    @Test
    public void hoveringGapsAndFooterNeverSelectsAnItem() {
        for (ContributionLayout layout : new ContributionLayout[] { ContributionLayout.fit(240),
            ContributionLayout.fit(360) }) {
            assertEquals(-1, layout.hoveredRow(0, 60));
            assertEquals(-1, layout.hoveredRow(10, 51));
            for (int row = 0; row < 6; row++) {
                assertEquals(row, layout.hoveredRow(10, layout.rowY(row) + 3));
                assertEquals(-1, layout.hoveredRow(10, layout.rowY(row) + layout.rowHeight - 1));
            }
            assertEquals(-1, layout.hoveredRow(10, layout.controlsY));
        }
    }
}
