package dev.aeoptimizer;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

@LateMixin
public final class LateMixins implements ILateMixinLoader {

    public String getMixinConfig() {
        return "mixins.aeorderoptimizer.late.json";
    }

    public List<String> getMixins(Set<String> mods) {
        return Arrays.asList(
            "CraftingGridMixin",
            "CpuMixin",
            "TaskProgressMixin",
            "SessionCountMixin",
            "DualityMixin",
            "CpuDiagnosticsMixin");
    }
}
