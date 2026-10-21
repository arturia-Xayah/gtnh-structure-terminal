package dev.aeoptimizer.packtest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.GsonBuilder;

import appeng.api.networking.crafting.ICraftingGrid;
import dev.aeoptimizer.OrderOptimizer;
import dev.aeoptimizer.network.OptimizerNetworks;
import dev.aeoptimizer.network.TileOptimizer;

/** Dedicated-server, runtime-recipe planning comparison only. No full production or client launch. */
final class StargateBenchmark {

    final OptimizerPackTest host;
    final List<Map<String, Object>> rows = new ArrayList<>();
    final List<Map<String, Object>> metadata = new ArrayList<>();
    LoadedStargateProbe probe;
    int ticks, waitUntil, mode, repeat, scenario;
    boolean setup, finished, switched;

    StargateBenchmark(OptimizerPackTest host) {
        this.host = host;
    }

    void tick() {
        if (finished) return;
        try {
            ticks++;
            if (!setup) {
                host.setup();
                setup = true;
                waitUntil = ticks + 160;
                return;
            }
            if (ticks < waitUntil) return;
            if (scenario == 2) {
                finish(null);
                return;
            }
            if (!switched) {
                if (mode == 2) {
                    OptimizerPackTest
                        .check(host.world.setBlock(8, 81, 8, OrderOptimizer.BLOCK, 0, 3), "optimizer placement");
                    TileOptimizer tile = (TileOptimizer) host.world.getTileEntity(8, 81, 8);
                    tile.getProxy()
                        .setOwner(host.player);
                    tile.detailed = false;
                } else host.world.setBlockToAir(8, 81, 8);
                switched = true;
                waitUntil = ticks + 60;
                return;
            }
            if (probe == null) {
                OptimizerPackTest
                    .check((OptimizerNetworks.find(host.grid()) != null) == (mode == 2), "incorrect network opt-in");
                host.cache = host.grid()
                    .getCache(ICraftingGrid.class);
                String name = scenario == 0 ? "original" : "dag";
                probe = new LoadedStargateProbe(
                    host.world,
                    host.grid(),
                    host.cache,
                    host.source,
                    name + "-order-input.json",
                    name,
                    mode,
                    repeat);
                metadata.add(new LinkedHashMap<>(probe.metadata));
                return;
            }
            if (!probe.tick()) return;
            rows.addAll(probe.rows);
            probe.detach();
            probe = null;
            mode++;
            if (mode == 3) {
                mode = 0;
                repeat++;
            }
            if (repeat == Integer.getInteger("aeoptimizer.stargateRepeats", 4)) {
                repeat = 0;
                scenario++;
            }
            switched = false;
            write(null);
        } catch (Throwable error) {
            error.printStackTrace();
            finish(error);
        }
    }

    void write(Throwable error) throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("completed", finished && error == null);
        report.put("client_launched", false);
        report.put("java", System.getProperty("java.version"));
        report.put("cases", rows);
        report.put("metadata", metadata);
        report.put("full_stargate_production_tested", false);
        if (error != null) report.put("fatal_error", error.toString());
        Files.write(
            Paths.get("ae-order-report.json"),
            new GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(report)
                .getBytes(StandardCharsets.UTF_8));
    }

    void finish(Throwable error) {
        finished = true;
        try {
            write(error);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (host.server != null) host.server.initiateShutdown();
    }
}
