package dev.aeoptimizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

import cpw.mods.fml.relauncher.FMLLaunchHandler;

@LateMixin
public final class LateMixins implements ILateMixinLoader {

    public String getMixinConfig() {
        return "mixins.aeorderoptimizer.late.json";
    }

    public List<String> getMixins(Set<String> mods) {
        List<String> mixins = new ArrayList<>(
            Arrays.asList(
                "CraftingGridMixin",
                "CpuMixin",
                "TaskProgressMixin",
                "SessionCountMixin",
                "DualityMixin",
                "CpuDiagnosticsMixin"));
        if (FMLLaunchHandler.side()
            .isClient()) {
            mixins.add("client.TerminalRepoAccessor");
            mixins.add("client.ItemRepoAccessor");
            mixins.add("client.PatternInputMixin");
            mixins.add("client.PatternTooltipMixin");
        }
        return mixins;
    }
}
