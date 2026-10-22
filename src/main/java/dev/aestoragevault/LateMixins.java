package dev.aestoragevault;

import java.util.Collections;
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
        return Collections.singletonList("VaultHatchSkinMixin");
    }
}
