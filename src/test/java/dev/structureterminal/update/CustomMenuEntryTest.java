package dev.structureterminal.update;

import static org.junit.Assert.*;

import net.minecraft.client.gui.GuiButton;

import org.junit.Test;

import dev.structureterminal.client.update.UpdateClient;
import lumien.custommainmenu.configuration.GuiConfig;
import lumien.custommainmenu.gui.GuiCustom;

/** Exercises the same live list CMM draws and hit-tests, without a display or a network request. */
public class CustomMenuEntryTest {

    @Test
    public void entrySurvivesFailureDisabledStartupAndMenuReinitialization() throws Exception {
        java.lang.reflect.Constructor<UpdateClient> constructor = UpdateClient.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        UpdateClient client = constructor.newInstance();
        client.pack = "2.9.0";
        GuiConfig config = new GuiConfig();
        config.name = "mainmenu";
        GuiCustom menu = new GuiCustom(config);
        menu.width = 480;
        menu.height = 260;
        java.util.List<GuiButton> transientButtons = new java.util.ArrayList<>();
        for (String state : new String[] { "idle", "current", "failed", "checking", "available", "unknownPack" }) {
            client.state = state;
            menu.getButtonList()
                .clear();
            client.attachMenuButton(menu, transientButtons);
            client.attachMenuButton(menu, transientButtons);
            assertEquals(
                1,
                menu.getButtonList()
                    .size());
            assertTrue(transientButtons.isEmpty());
            GuiButton entry = menu.getButtonList()
                .get(0);
            assertTrue(entry.visible && entry.enabled);
            assertEquals(6, menu.width - entry.xPosition - entry.width);
            assertEquals(6, entry.yPosition);
            assertTrue(entry.mousePressed(null, entry.xPosition + 1, entry.yPosition + 1));
            assertEquals(state, client.state);
        }
        config.name = "credits";
        menu.getButtonList()
            .clear();
        client.attachMenuButton(menu, transientButtons);
        assertTrue(
            menu.getButtonList()
                .isEmpty());
    }
}
