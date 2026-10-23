package dev.aestoragevault.client;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Local preference, never part of a machine, world or server packet. */
@SideOnly(Side.CLIENT)
public final class VaultClientPreferences {

    private static Configuration config;
    private static boolean animations = true;

    private VaultClientPreferences() {}

    public static void load(File file) {
        config = new Configuration(file);
        config.load();
        animations = config.getBoolean(
            "storageAnimations",
            "client",
            true,
            "Animate the storage dashboard. Disable for a static/reduced-motion interface.");
        if (config.hasChanged()) config.save();
    }

    public static boolean animations() {
        return animations;
    }

    public static void setAnimations(boolean value) {
        animations = value;
        if (config != null) {
            config.get("client", "storageAnimations", true)
                .set(value);
            config.save();
        }
    }
}
