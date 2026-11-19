package dev.structureterminal.client.update;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import lumien.custommainmenu.gui.GuiCustom;

/** Loaded only for CMM screens. Vanilla title screens use the Forge event list directly. */
final class CustomMenuAccess {

    private CustomMenuAccess() {}

    static boolean isMain(GuiScreen screen) {
        return "mainmenu".equals(((GuiCustom) screen).guiConfig.name);
    }

    static List<GuiButton> buttons(GuiScreen screen) {
        return ((GuiCustom) screen).getButtonList();
    }
}
