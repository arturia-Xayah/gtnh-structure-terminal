package dev.aeoptimizer.planning;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.data.IAEStack;
import dev.aeoptimizer.OrderOptimizer;

public final class Reports {

    public static final java.util.concurrent.atomic.AtomicLong DETAILED_REPORTS = new java.util.concurrent.atomic.AtomicLong();

    public static java.util.Map<IAEStack<?>, java.util.List<ICraftingPatternDetails>> catalog(
        appeng.crafting.v2.CraftingContext context) {
        java.util.Map<IAEStack<?>, java.util.List<ICraftingPatternDetails>> index = new java.util.HashMap<>();
        if (context.craftingGrid instanceof dev.aeoptimizer.api.PatternCatalog) {
            for (ICraftingPatternDetails pattern : ((dev.aeoptimizer.api.PatternCatalog) context.craftingGrid)
                .aeo$registeredPatterns())
                for (IAEStack<?> output : pattern.getCondensedAEOutputs())
                    index.computeIfAbsent(output, key -> new java.util.ArrayList<>())
                        .add(pattern);
        }
        return index;
    }

    private Reports() {}

    public static String describe(ICraftingPatternDetails pattern) {
        StringBuilder out = new StringBuilder("pattern#");
        out.append(fingerprint(pattern))
            .append(" [");
        append(out, pattern.getCondensedAEInputs());
        out.append(" -> ");
        append(out, pattern.getCondensedAEOutputs());
        return out.append(']')
            .toString();
    }

    private static void append(StringBuilder out, IAEStack<?>[] stacks) {
        for (int i = 0; i < stacks.length; i++) {
            if (i > 0) out.append(" + ");
            out.append(stacks[i].getDisplayName())
                .append(" x")
                .append(stacks[i].getStackSize());
        }
    }

    private static String fingerprint(ICraftingPatternDetails pattern) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(
                    pattern.getPattern()
                        .writeToNBT(new NBTTagCompound())
                        .toString()
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder();
            for (int i = 0; i < 8; i++) value.append(String.format("%02x", hash[i] & 255));
            return value.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void chat(String text, BaseActionSource source) {
        if (source instanceof PlayerSource) {
            String summary = text.length() > 1200
                ? text.substring(0, 1200) + "... [完整报告见服务器日志 / Full report: server log]"
                : text;
            ((PlayerSource) source).player.addChatMessage(new ChatComponentText(summary));
        }
    }

    public static void error(String text, BaseActionSource source) {
        OrderOptimizer.LOG.warn(text);
        chat(text, source);
    }

    public static void detail(String text, BaseActionSource source) {
        DETAILED_REPORTS.incrementAndGet();
        OrderOptimizer.LOG.info(text);
        chat(text, source);
    }
}
