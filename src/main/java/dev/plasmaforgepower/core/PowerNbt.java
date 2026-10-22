package dev.plasmaforgepower.core;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Explicit configuration-only payload shared by world saves, Matter Manipulator and Data Orbs. */
public final class PowerNbt {

    public static final String KEY = "plasmaforgepower:limits";
    public static final String TYPE = "plasmaforgepower:dtpf-config-v1";

    private PowerNbt() {}

    public static NBTTagCompound write(PowerSettings settings) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("type", TYPE);
        tag.setInteger("version", 1);
        NBTTagList list = new NBTTagList();
        for (int color = -1; color < 16; color++) {
            PowerLimit limit = settings.get(color);
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("color", color);
            entry.setLong("eut", limit.eut());
            entry.setInteger("tier", limit.tier());
            list.appendTag(entry);
        }
        tag.setTag("groups", list);
        return tag;
    }

    /** Reject unknown, incomplete, duplicate or malformed payloads without partially changing a machine. */
    public static PowerSettings read(NBTTagCompound tag) {
        if (tag == null || !TYPE.equals(tag.getString("type"))
            || !integer(tag, "version")
            || tag.getLong("version") != 1) return null;
        NBTTagList list = tag.getTagList("groups", 10);
        if (list.tagCount() != 17) return null;
        PowerSettings result = new PowerSettings();
        boolean[] seen = new boolean[17];
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            // MM's JSON round trip narrows integer tags (e.g. int 1 becomes byte 1). Keep numeric meaning, not width.
            if (!integer(entry, "color") || !integer(entry, "eut") || !integer(entry, "tier")) return null;
            long rawColor = entry.getLong("color"), rawTier = entry.getLong("tier");
            if (rawColor < -1 || rawColor > 15 || rawTier < 0 || rawTier >= PowerLimit.TIERS.length) return null;
            int color = (int) rawColor;
            if (seen[color + 1]) return null;
            seen[color + 1] = true;
            try {
                result.set(color, PowerLimit.ofEUt(entry.getLong("eut"), (int) rawTier));
            } catch (IllegalArgumentException invalid) {
                return null;
            }
        }
        return result;
    }

    private static boolean integer(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key)) return false;
        int type = tag.getTag(key)
            .getId();
        return type >= 1 && type <= 4;
    }
}
