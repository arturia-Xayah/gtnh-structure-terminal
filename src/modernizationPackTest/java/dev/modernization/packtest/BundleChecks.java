package dev.modernization.packtest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.server.MinecraftServer;

import com.google.gson.GsonBuilder;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import dev.structureterminal.Tags;

/** Verifies all compatibility namespaces are live together in the single test artifact. */
@Mod(
    modid = "modernizationpackchecks",
    name = "Modernization bundle checks",
    version = "1",
    dependencies = "required-after:structureterminal;required-after:aeorderoptimizer;required-after:plasmaforgepower;required-after:aestoragevault")
public final class BundleChecks {

    @Mod.EventHandler
    public void started(FMLServerStartedEvent event) throws Exception {
        if (!Boolean.getBoolean("modernization.tests")) return;
        Map<String, Object> report = new LinkedHashMap<>();
        Map<String, String> modules = new LinkedHashMap<>();
        for (String id : new String[] { "structureterminal", "aeorderoptimizer", "plasmaforgepower",
            "aestoragevault" }) {
            ModContainer container = Loader.instance()
                .getIndexedModList()
                .get(id);
            check(container != null, "missing module " + id);
            check(Tags.VERSION.equals(container.getVersion()), "module version mismatch " + id);
            check(
                container.getName()
                    .startsWith("GTNH现代化升级"),
                "old display name " + id);
            check(
                "assets/structureterminal/textures/items/structure_terminal.png"
                    .equals(container.getMetadata().logoFile),
                "logo mismatch " + id);
            if (!"structureterminal".equals(id))
                check("structureterminal".equals(container.getMetadata().parent), "parent mismatch " + id);
            modules.put(id, container.getVersion());
        }
        check(GameRegistry.findItem("structureterminal", "structure_terminal") != null, "terminal registry lost");
        check(GameRegistry.findBlock("aeorderoptimizer", "core") != null, "optimizer registry lost");
        for (String name : new String[] { "controller", "access", "output", "casing" })
            check(GameRegistry.findBlock("aestoragevault", name) != null, "vault registry lost " + name);
        report.put("passed", true);
        report.put("modules", modules);
        report.put("client_launched", false);
        Files.write(
            MinecraftServer.getServer()
                .getFile("modernization-modules.json")
                .toPath(),
            new GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(report)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
