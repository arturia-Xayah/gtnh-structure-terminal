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
        Map<String, String> dependencies = new LinkedHashMap<>();
        for (String[] expected : new String[][] { { "gregtech", "5.09.54.265" },
            { "appliedenergistics2", "rv3-beta-1095-GTNH" }, { "structurelib", "1.4.47" },
            { "modularui2", "2.3.94-1.7.10" }, { "gtnhlib", "0.11.54" }, { "NotEnoughItems", "2.8.171-GTNH" } }) {
            ModContainer dependency = Loader.instance()
                .getIndexedModList()
                .get(expected[0]);
            check(dependency != null, "missing dependency " + expected[0]);
            check(
                expected[1].equals(dependency.getVersion()),
                "wrong runtime dependency " + expected[0] + ": " + dependency.getVersion());
            dependencies.put(expected[0], dependency.getVersion());
        }
        report.put("dependencies", dependencies);
        report.put("pack", System.getProperty("modernization.packVersion", "2.9.0"));
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
