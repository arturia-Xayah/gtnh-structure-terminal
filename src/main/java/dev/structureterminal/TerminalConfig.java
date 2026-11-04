package dev.structureterminal;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public final class TerminalConfig {

    public static int cells = 100000;
    public static int scanMillis = 500;
    public static int budget = 25;
    public static int rounds = 10000;
    public static int reach = 8;
    public static int buildMillis = 1000;
    public static boolean demolish = true;
    public static boolean demolishTiles;

    private TerminalConfig() {}

    public static void load(File file) {
        Configuration c = new Configuration(file);
        c.load();
        cells = c.getInt("maxPreviewCells", "limits", cells, 256, 1000000, "Maximum cells in a structure scan.");
        scanMillis = c
            .getInt("maxScanMillis", "limits", scanMillis, 50, 5000, "Abort excessively slow structure scans.");
        budget = c.getInt(
            "placementBudget",
            "limits",
            budget,
            1,
            200,
            "Blocks per tick for replacement and demolition; normal builds are synchronous.");
        rounds = c.getInt("maxBuildRounds", "limits", rounds, 1, 100000, "Maximum rounds before a job stops.");
        buildMillis = c.getInt(
            "maxInstantBuildMillis",
            "limits",
            buildMillis,
            50,
            5000,
            "Safety timeout for synchronous construction; an incomplete request stops, never silently continues next tick.");
        reach = c.getInt("controllerReach", "limits", reach, 4, 16, "Player must remain near the controller.");
        demolish = c.getBoolean(
            "allowDemolition",
            "features",
            true,
            "Allow explicitly confirmed removal of matching empty structure blocks.");
        demolishTiles = c.getBoolean(
            "allowTileEntityDemolition",
            "features",
            false,
            "Opt-in: remove inspectably empty tile entities. Disabled until pack-specific inventory/cover behavior is verified.");
        if (c.hasChanged()) c.save();
    }
}
