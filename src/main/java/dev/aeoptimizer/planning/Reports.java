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

    private static net.minecraft.util.IChatComponent product(IAEStack<?> stack,
        java.util.Collection<ICraftingPatternDetails> patterns, BaseActionSource source,
        appeng.crafting.v2.CraftingContext context) {
        net.minecraft.util.EnumChatFormatting[] colors = { net.minecraft.util.EnumChatFormatting.AQUA,
            net.minecraft.util.EnumChatFormatting.GREEN, net.minecraft.util.EnumChatFormatting.LIGHT_PURPLE,
            net.minecraft.util.EnumChatFormatting.GOLD, net.minecraft.util.EnumChatFormatting.YELLOW };
        ChatComponentText link = new ChatComponentText(
            stack.getDisplayName()
                .replaceAll("(?i)\\u00a7[0-9a-fk-or]", ""));
        link.getChatStyle()
            .setColor(colors[(stack.hashCode() & Integer.MAX_VALUE) % colors.length])
            .setUnderlined(true);
        if (source instanceof PlayerSource
            && ((PlayerSource) source).player instanceof net.minecraft.entity.player.EntityPlayerMP) {
            String command = PatternLocations.remember(
                (net.minecraft.entity.player.EntityPlayerMP) ((PlayerSource) source).player,
                stack.getDisplayName(),
                patterns,
                context);
            link.getChatStyle()
                .setChatClickEvent(
                    new net.minecraft.event.ClickEvent(net.minecraft.event.ClickEvent.Action.RUN_COMMAND, command));
            link.getChatStyle()
                .setChatHoverEvent(
                    new net.minecraft.event.HoverEvent(
                        net.minecraft.event.HoverEvent.Action.SHOW_TEXT,
                        new net.minecraft.util.ChatComponentTranslation("aeoptimizer.locate.hint")));
        }
        return link;
    }

    public static void alternatives(IAEStack<?> stack, java.util.List<ICraftingPatternDetails> patterns,
        BaseActionSource source, appeng.crafting.v2.CraftingContext context) {
        DETAILED_REPORTS.incrementAndGet();
        OrderOptimizer.LOG.info("AEO_ALTERNATIVES: {} recipes={}", stack.getDisplayName(), patterns.size());
        if (source instanceof PlayerSource) ((PlayerSource) source).player.addChatMessage(
            new net.minecraft.util.ChatComponentTranslation(
                "aeoptimizer.report.alternatives",
                product(stack, patterns, source, context),
                patterns.size()));
    }

    public static void cycle(dev.aeoptimizer.core.StrictGraph.InvalidPath error,
        java.util.Map<IAEStack<?>, it.unimi.dsi.fastutil.longs.LongObjectPair<ICraftingPatternDetails>> chosen,
        BaseActionSource source, appeng.crafting.v2.CraftingContext context) {
        OrderOptimizer.LOG.warn(error.getMessage());
        if (!(source instanceof PlayerSource)) return;
        net.minecraft.util.IChatComponent line = new net.minecraft.util.ChatComponentTranslation(
            "aeoptimizer.report.cycle");
        for (int i = 0; i < error.products.size(); i++) {
            IAEStack<?> stack = (IAEStack<?>) error.products.get(i);
            if (i > 0 && i % 16 == 0) {
                ((PlayerSource) source).player.addChatMessage(line);
                line = new ChatComponentText("→ ");
            } else if (i > 0) line.appendText(" → ");
            it.unimi.dsi.fastutil.longs.LongObjectPair<ICraftingPatternDetails> choice = chosen.get(stack);
            net.minecraft.util.IChatComponent link = product(
                stack,
                choice == null ? java.util.Collections.emptyList()
                    : java.util.Collections.singletonList(choice.right()),
                source,
                context);
            net.minecraft.util.EnumChatFormatting[] palette = { net.minecraft.util.EnumChatFormatting.AQUA,
                net.minecraft.util.EnumChatFormatting.LIGHT_PURPLE, net.minecraft.util.EnumChatFormatting.GOLD,
                net.minecraft.util.EnumChatFormatting.GREEN, net.minecraft.util.EnumChatFormatting.YELLOW };
            link.getChatStyle()
                .setColor(palette[(i == error.products.size() - 1 ? 0 : i) % palette.length]);
            line.appendSibling(link);
        }
        ((PlayerSource) source).player.addChatMessage(line);
    }

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
