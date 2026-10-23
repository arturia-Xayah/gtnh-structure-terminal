package dev.aestoragevault.ui.dashboard;

/** GUI-local coordinates, shared by the native container, client renderer and headless layout checks. */
public final class VaultLayout {

    public final boolean compact;
    public final int width, height, panelY, panelHeight, metricX, gaugeX, gaugeY, radius;
    public final int inventoryY, hotbarY, typesY, inventoryLabelY, statusX, statusY, statusWidth;
    public final int automaticY, controlsY;

    private VaultLayout(boolean compact) {
        this.compact = compact;
        width = compact ? 304 : 336;
        height = compact ? 224 : 284;
        panelY = compact ? 31 : 36;
        panelHeight = compact ? 81 : 110;
        metricX = compact ? 98 : 126;
        gaugeX = compact ? 49 : 67;
        gaugeY = compact ? 72 : 91;
        radius = compact ? 29 : 40;
        inventoryY = compact ? 136 : 187;
        hotbarY = compact ? 196 : 247;
        typesY = compact ? 122 : 154;
        inventoryLabelY = compact ? 122 : 174;
        statusX = compact ? 184 : 188;
        statusY = compact ? 120 : 154;
        statusWidth = width - statusX - 10;
        automaticY = compact ? 171 : 214;
        controlsY = compact ? 192 : 236;
    }

    public static VaultLayout full() {
        return new VaultLayout(false);
    }

    public static VaultLayout fit(int viewportWidth, int viewportHeight) {
        return new VaultLayout(viewportWidth < 352 || viewportHeight < 300);
    }

    public int slotX(int slot) {
        return 12 + (slot % 9) * 18;
    }

    public int slotY(int slot) {
        return slot < 27 ? inventoryY + (slot / 9) * 18 : hotbarY;
    }

    public int buttonWidth() {
        return statusWidth - 20;
    }
}
