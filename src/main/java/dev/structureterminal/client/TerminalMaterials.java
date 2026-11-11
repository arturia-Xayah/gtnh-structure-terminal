package dev.structureterminal.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import org.apache.commons.lang3.tuple.Pair;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.util.GlassTier;
import gregtech.common.blocks.BlockCasings5;

/** UI entries retain native channel IDs even when sorted by voltage tier. */
public final class TerminalMaterials {

    public static final class Entry {

        public final int value, tier;
        public final ItemStack icon;

        public Entry(int value, int tier, ItemStack icon) {
            this.value = value;
            this.tier = tier;
            this.icon = icon;
        }
    }

    public static List<Entry> coils() {
        List<Entry> entries = new ArrayList<Entry>();
        entries.add(new Entry(0, 0, null));
        for (byte tier = 0; tier <= HeatingCoilLevel.getMaxTier(); tier++) {
            entries.add(
                new Entry(
                    tier + 1,
                    tier + 1,
                    new ItemStack(
                        GregTechAPI.sBlockCasings5,
                        1,
                        BlockCasings5.getMetaFromCoilHeat(HeatingCoilLevel.getFromTier(tier)))));
        }
        return entries;
    }

    public static List<Entry> glasses() {
        List<Entry> entries = new ArrayList<Entry>();
        Set<Integer> seen = new HashSet<Integer>();
        for (Pair<Block, Integer> glass : GlassTier.getGlassList()) {
            if (glass == null || glass.getLeft() == null) continue;
            int value = GlassTier.getGlassChannelValue(glass.getLeft(), glass.getRight());
            Integer tier = GlassTier.getGlassBlockTier(glass.getLeft(), glass.getRight());
            if (value > 0 && tier != null && tier >= 3 && tier < 3 + GlassTier.getMaxTierIndex() && seen.add(value))
                entries.add(new Entry(value, tier, new ItemStack(glass.getLeft(), 1, glass.getRight())));
        }
        entries.sort(
            Comparator.comparingInt((Entry e) -> e.tier)
                .thenComparingInt(e -> e.value));
        entries.add(0, new Entry(0, 0, null));
        return entries;
    }

    public static List<Entry> glassTiers() {
        List<Entry> result = new ArrayList<>();
        result.add(new Entry(0, 0, null));
        Set<Integer> seen = new HashSet<>();
        for (Entry e : glasses())
            if (e.value > 0 && seen.add(e.tier)) result.add(new Entry(-e.tier - 1, e.tier, e.icon));
        return result;
    }

    public static List<Entry> variants(int tier) {
        List<Entry> result = new ArrayList<>();
        for (Entry e : glasses()) if (e.value > 0 && e.tier == tier) result.add(e);
        result.add(0, new Entry(0, tier, result.isEmpty() ? null : result.get(0).icon));
        return result;
    }

    private TerminalMaterials() {}
}
