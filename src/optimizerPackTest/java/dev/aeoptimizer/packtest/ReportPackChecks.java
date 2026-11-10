package dev.aeoptimizer.packtest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.event.ClickEvent;
import net.minecraft.util.IChatComponent;

import appeng.util.item.AEItemStack;
import dev.aeoptimizer.planning.PatternLocations;

final class ReportPackChecks {

    private static List<IChatComponent> links(OptimizerPackTest t) {
        List<IChatComponent> links = new ArrayList<>();
        IChatComponent message = t.capturedChat.get(t.capturedChat.size() - 1);
        OptimizerPackTest.check(
            !message.getUnformattedText()
                .contains("pattern#"),
            "verbose recipe leaked into chat");
        for (Object part : message) {
            IChatComponent text = (IChatComponent) part;
            ClickEvent event = text.getChatStyle()
                .getChatClickEvent();
            if (event == null) continue;
            OptimizerPackTest.check(
                text.getChatStyle()
                    .getUnderlined()
                    && text.getChatStyle()
                        .getColor() != null,
                "product is not colored and underlined");
            OptimizerPackTest.check(
                event.getAction() == ClickEvent.Action.RUN_COMMAND && event.getValue()
                    .startsWith("/aeolocate "),
                "invalid location command");
            links.add(text);
        }
        return links;
    }

    static void alternatives(OptimizerPackTest t) {
        OptimizerPackTest.check(links(t).size() == 1, "alternatives report must have one product link");
        t.row("compact_alternatives_count_and_underlined_product_link", true);
    }

    static void cycle(OptimizerPackTest t) {
        List<IChatComponent> links = links(t);
        OptimizerPackTest.check(links.size() == 3, "two-node cycle should be a closed three-product chain");
        OptimizerPackTest.check(
            links.get(0)
                .getChatStyle()
                .getColor()
                != links.get(1)
                    .getChatStyle()
                    .getColor(),
            "cycle product colors collide");
        t.row("compact_cycle_links_have_distinct_colors", true);
    }

    static void nativeLocation(OptimizerPackTest t) throws Exception {
        appeng.crafting.v2.CraftingContext context = new appeng.crafting.v2.CraftingContext(
            t.world,
            t.grid(),
            t.source);
        List<appeng.api.networking.crafting.ICraftingPatternDetails> patterns = context
            .getPrecisePatternsFor(AEItemStack.create(OptimizerPackTest.token("real_interface")));
        OptimizerPackTest.check(!patterns.isEmpty(), "native interface has no pattern");
        String command = PatternLocations.remember(t.player, "fixture", patterns, context);
        int before = t.capturedChat.size();
        new PatternLocations().processCommand(t.player, new String[] { command.substring("/aeolocate ".length()) });
        OptimizerPackTest.check(t.capturedChat.size() > before, "location command emitted no coordinates");
        String report = t.capturedChat.get(before)
            .getUnformattedText();
        OptimizerPackTest.check(
            report.contains("(8, 80, 9)") && report.contains("[0]"),
            "native pattern coordinates wrong: " + report);
        t.row("native_interface_report_click_resolves_world_and_coordinates", true);
        net.minecraft.world.World original = t.realInterface.getWorldObj();
        net.minecraft.world.World other = net.minecraft.server.MinecraftServer.getServer()
            .worldServerForDimension(-1);
        try {
            t.realInterface.setWorldObj(other);
            String remote = PatternLocations.remember(t.player, "remote", patterns, context);
            before = t.capturedChat.size();
            new PatternLocations().processCommand(t.player, new String[] { remote.substring("/aeolocate ".length()) });
            String dimensionReport = t.capturedChat.get(before)
                .getUnformattedText();
            OptimizerPackTest.check(
                dimensionReport.contains("[-1]") && dimensionReport.contains(other.provider.getDimensionName())
                    && dimensionReport.contains("(8, 80, 9)"),
                "cross-dimension coordinates missing");
            t.row("report_provider_in_other_dimension_names_dimension_and_coordinates", true);
        } finally {
            t.realInterface.setWorldObj(original);
        }
        PatternLocations.clear();
        before = t.capturedChat.size();
        new PatternLocations().processCommand(t.player, new String[] { command.substring("/aeolocate ".length()) });
        OptimizerPackTest.check(
            t.capturedChat.size() == before + 1 && !t.capturedChat.get(before)
                .getUnformattedText()
                .contains("(8, 80, 9)"),
            "expired token exposed old coordinates");
        t.row("cleared_location_token_does_not_resolve", true);
    }
}
