package dev.structureterminal.bc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

@LateMixin
public final class LateMixins implements ILateMixinLoader {

    public String getMixinConfig() {
        return "mixins.structureterminal.bc.json";
    }

    public List<String> getMixins(Set<String> mods) {
        List<String> mixins = new ArrayList<>();
        boolean client = cpw.mods.fml.relauncher.FMLLaunchHandler.side()
            .isClient();
        if (mods.contains("BuildCraft|Builders")) {
            mixins.addAll(Arrays.asList("FillerMixin", "ArchitectMixin", "RangeBlockMixin", "RangeSyncMixin"));
            if (mods.contains("matter-manipulator")) {
                mixins.add("PendingRangeMixin");
                if (client) mixins.add("PastePreviewMixin");
            }
        }
        return mixins;
    }
}
