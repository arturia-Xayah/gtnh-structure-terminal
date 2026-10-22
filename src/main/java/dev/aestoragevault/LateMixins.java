package dev.aestoragevault;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

@LateMixin
public final class LateMixins implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.aestoragevault.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> mods) {
        List<String> mixins = new ArrayList<>(Arrays.asList("VaultHatchSkinMixin", "NativeBusOwnershipMixin"));
        if (cpw.mods.fml.relauncher.FMLLaunchHandler.side()
            .isClient()) mixins.add("client.VaultCasingRenderMixin");
        return mixins;
    }
}
