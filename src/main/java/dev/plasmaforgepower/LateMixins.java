package dev.plasmaforgepower;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

@LateMixin
public final class LateMixins implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.plasmaforgepower.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        return Arrays.asList(
            "PlasmaForgeMixin",
            "RecipePowerGateMixin",
            "DataOrbMixin",
            "FusionPowerMixin",
            "FusionContextMixin",
            "NormalFusionRecipeMixin",
            "CompactFusionRecipeMixin",
            "FusionGuiMixin");
    }
}
