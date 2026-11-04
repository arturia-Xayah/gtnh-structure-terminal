package dev.structureterminal.client.update;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Mouse;

import dev.structureterminal.Tags;
import dev.structureterminal.update.ReleaseCatalog;

public final class UpdateScreen extends GuiScreen implements net.minecraft.client.gui.GuiYesNoCallback {

    private final GuiScreen parent;
    private final UpdateClient updater = UpdateClient.INSTANCE;
    private int left, top, panelWidth, panelHeight, scroll, linesVisible, lineCount;
    private String previousState = "";

    public UpdateScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        panelWidth = Math.min(400, width - 16);
        panelHeight = Math.min(300, height - 12);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        int half = (panelWidth - 28) / 2;
        int third = (panelWidth - 32) / 3;
        buttonList.clear();
        buttonList.add(new UpdateButton(0, left + 10, top + panelHeight - 40, third, ""));
        buttonList.add(new UpdateButton(1, left + 16 + third, top + panelHeight - 40, third, UpdateClient.tr("later")));
        buttonList
            .add(new UpdateButton(2, left + 22 + third * 2, top + panelHeight - 40, third, UpdateClient.tr("skip")));
        buttonList.add(new UpdateButton(3, left + 10, top + panelHeight - 88, half, ""));
        buttonList.add(new UpdateButton(4, left + 18 + half, top + panelHeight - 88, half, ""));
        buttonList.add(new UpdateButton(5, left + 10, top + panelHeight - 64, half, ""));
        buttonList
            .add(new UpdateButton(6, left + 18 + half, top + panelHeight - 64, half, UpdateClient.tr("releases")));
        refresh();
    }

    private void refresh() {
        for (Object entry : buttonList) {
            GuiButton b = (GuiButton) entry;
            boolean installing = updater.state.equals("installing");
            b.enabled = !installing;
            switch (b.id) {
                case 0:
                    b.displayString = UpdateClient.tr(
                        updater.state.equals("ready") ? "install"
                            : updater.state.equals("downloading") ? "cancel"
                                : updater.release != null && !updater.state.equals("failed") ? "download" : "check");
                    b.enabled = !installing && !updater.state.equals("checking");
                    break;
                case 1:
                    b.displayString = UpdateClient.tr(updater.busy() ? "background" : "later");
                    break;
                case 2:
                    b.enabled = !updater.busy() && updater.release != null
                        && updater.serverVersion == null
                        && !updater.state.equals("ready");
                    break;
                case 3:
                    b.displayString = option("startup", updater.enabled);
                    break;
                case 4:
                    b.displayString = option("previews", updater.previews);
                    break;
                case 5:
                    b.displayString = option("serverAuto", updater.serverAutomatic);
                    break;
                default:
                    break;
            }
        }
    }

    private String option(String key, boolean value) {
        return UpdateClient.tr(key) + ": " + UpdateClient.tr(value ? "on" : "off");
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) return;
        switch (button.id) {
            case 0:
                if (updater.state.equals("ready")) updater.installAndExit();
                else if (updater.state.equals("downloading")) updater.cancelDownload();
                else if (updater.release != null && !updater.state.equals("failed")) updater.download();
                else updater.check(updater.serverVersion);
                break;
            case 1:
                mc.displayGuiScreen(parent);
                break;
            case 2:
                updater.skip();
                mc.displayGuiScreen(parent);
                break;
            case 3:
                updater.enabled = !updater.enabled;
                updater.save();
                break;
            case 4:
                updater.previews = !updater.previews;
                updater.save();
                updater.check(updater.serverVersion);
                break;
            case 5:
                updater.serverAutomatic = !updater.serverAutomatic;
                updater.save();
                break;
            case 6:
                mc.displayGuiScreen(new GuiConfirmOpenLink(this, ReleaseCatalog.WEB, 6, false));
                break;
            default:
                break;
        }
        refresh();
    }

    @Override
    public void confirmClicked(boolean accepted, int id) {
        if (accepted && id == 6) {
            try {
                java.awt.Desktop.getDesktop()
                    .browse(URI.create(ReleaseCatalog.WEB));
            } catch (Exception error) {
                updater.detail = UpdateClient.tr("browserFailed") + " " + ReleaseCatalog.WEB;
            }
        }
        mc.displayGuiScreen(this);
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == 1 && !updater.state.equals("installing")) mc.displayGuiScreen(parent);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0)
            scroll = Math.max(0, Math.min(Math.max(0, lineCount - linesVisible), scroll + (wheel < 0 ? 3 : -3)));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (UpdateClient.isMenu(parent)) parent.drawScreen(-1000, -1000, partialTicks);
        else drawDefaultBackground();
        drawRect(0, 0, width, height, 0xA8000000);
        drawRect(left, top, left + panelWidth, top + panelHeight, 0xF0111E25);
        drawRect(left, top, left + panelWidth, top + 2, 0xFF40FFFF);
        drawCenteredString(fontRendererObj, UpdateClient.tr("title"), width / 2, top + 10, 0x40FFFF);
        drawString(
            fontRendererObj,
            UpdateClient.tr("versions", Tags.VERSION, updater.pack == null ? "?" : updater.pack),
            left + 10,
            top + 28,
            0xCCDDE5);
        String target = updater.serverVersion != null ? UpdateClient.tr("serverTarget", updater.serverVersion)
            : updater.release == null ? UpdateClient.tr("optional")
                : UpdateClient.tr("target", updater.release.version);
        drawString(fontRendererObj, target, left + 10, top + 41, 0xFFB83B);
        String status = UpdateClient.tr("state." + updater.state);
        List<String> lines = new ArrayList<String>();
        lines.addAll(fontRendererObj.listFormattedStringToWidth(status, panelWidth - 24));
        if (updater.state.equals("downloading") && updater.release != null) {
            double fraction = Math.min(1, (double) updater.downloaded / updater.release.size);
            drawRect(left + 10, top + 57, left + panelWidth - 10, top + 61, 0xFF344751);
            drawRect(left + 10, top + 57, left + 10 + (int) ((panelWidth - 20) * fraction), top + 61, 0xFF40FFFF);
            lines.add(String.format(java.util.Locale.ROOT, "%.0f%%", fraction * 100));
        }
        if (!updater.lastInstallMessage.isEmpty())
            lines.addAll(fontRendererObj.listFormattedStringToWidth(updater.lastInstallMessage, panelWidth - 24));
        if (!updater.detail.isEmpty())
            lines.addAll(fontRendererObj.listFormattedStringToWidth(updater.detail.replace('§', '?'), panelWidth - 24));
        if (updater.release != null && !updater.release.notes.isEmpty()) {
            lines.add("");
            for (String text : updater.release.notes.replace('§', '?')
                .split("\\r?\\n")) lines.addAll(fontRendererObj.listFormattedStringToWidth(text, panelWidth - 24));
        }
        if (!previousState.equals(updater.state)) {
            previousState = updater.state;
            scroll = 0;
        }
        linesVisible = Math.max(1, (panelHeight - 164) / 10);
        lineCount = lines.size();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lineCount - linesVisible)));
        for (int i = 0; i < linesVisible && scroll + i < lines.size(); i++)
            drawString(fontRendererObj, lines.get(scroll + i), left + 10, top + 66 + 10 * i, 0xBFD0D8);
        if (lineCount > linesVisible) drawString(
            fontRendererObj,
            (scroll + 1) + "/" + lineCount,
            left + panelWidth - 42,
            top + panelHeight - 100,
            0x40FFFF);
        drawCenteredString(
            fontRendererObj,
            UpdateClient.tr("restartHint"),
            width / 2,
            top + panelHeight - 14,
            0x81949F);
        refresh();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
