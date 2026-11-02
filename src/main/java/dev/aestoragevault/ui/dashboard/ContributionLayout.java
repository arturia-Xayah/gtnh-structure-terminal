package dev.aestoragevault.ui.dashboard;

/** Shared geometry for the six-row contribution page and its headless bounds checks. */
public final class ContributionLayout {

    public static final int ROWS = 6, WIDTH = 304;
    public final int height, rowHeight, controlsY;

    private ContributionLayout(boolean compact) {
        height = compact ? 224 : 266;
        rowHeight = compact ? 24 : 30;
        controlsY = height - 27;
    }

    public static ContributionLayout fit(int viewportHeight) {
        return new ContributionLayout(viewportHeight < 282);
    }

    public int rowY(int row) {
        if (row < 0 || row >= ROWS) throw new IllegalArgumentException("Invalid row");
        return 52 + row * rowHeight;
    }

    public int hoveredRow(int x, int y) {
        if (x < 8 || x >= WIDTH - 8 || y < 52) return -1;
        int row = (y - 52) / rowHeight;
        return row < ROWS && y < rowY(row) + rowHeight - 2 ? row : -1;
    }
}
