package dev.plasmaforgepower.packtest;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.google.gson.GsonBuilder;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureWalker;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.plasmaforgepower.api.FusionPowerAccess;
import dev.plasmaforgepower.core.PowerLimit;
import dev.plasmaforgepower.core.PowerNbt;
import dev.plasmaforgepower.core.PowerSettings;
import dev.plasmaforgepower.ui.PowerPanel;
import goodgenerator.blocks.tileEntity.base.MTELargeFusionComputer;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTRecipeConstants;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.tileentities.machines.multi.MTEFusionComputer;

/** Test-only, isolated real dedicated server: native structures, native recipe logic and native ticks. */
@Mod(
    modid = "fusionpowertest",
    name = "Fusion Power Backend Tests",
    version = "test",
    dependencies = "required-after:plasmaforgepower")
public final class FusionPowerSuite {

    private static final int[] IDS = { 1193, 1194, 1195, 965, 975, 32019, 32020, 32021, 32022, 32023 };
    private final List<Map<String, Object>> rows = new ArrayList<>();
    private final List<Rig> hot = new ArrayList<>();
    private WorldServer world;
    private MinecraftServer server;
    private FakePlayer player;
    private Fluid ia, ib, oa, ob, shared;
    private int index, ticks;
    private boolean done, initialized;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (Boolean.getBoolean("plasmaforgepower.fusionTests") && FMLCommonHandler.instance()
            .getSide()
            .isServer())
            FMLCommonHandler.instance()
                .bus()
                .register(this);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;
        try {
            if (!initialized) {
                setup();
                initialized = true;
                return;
            }
            if (index < IDS.length) {
                Rig rig = build(IDS[index], index++);
                checks(rig);
                if (rig.id == 1194 || rig.id == 32020) hot.add(rig);
                return;
            }
            ticks++;
            check(ticks < 1800, "hot-switch tick timeout");
            boolean finished = true;
            for (Rig r : hot) {
                advance(r);
                finished &= r.stage == 5;
            }
            if (finished) finish();
        } catch (Throwable failure) {
            failure.printStackTrace();
            row("fatal", false, failure.toString());
            try {
                finish();
            } catch (Exception nested) {
                server.initiateShutdown();
            }
        }
    }

    private void setup() {
        server = MinecraftServer.getServer();
        world = server.worldServerForDimension(0);
        check(server.isDedicatedServer(), "not a dedicated server");
        check(
            world.getWorldInfo()
                .getWorldName()
                .equals("forge-power-validation"),
            "wrong disposable world");
        player = new FakePlayer(
            world,
            new GameProfile(UUID.fromString("ae000000-0000-4000-8000-000000000035"), "FusionPowerTest"));
        player.capabilities.allowEdit = true;
        ia = fluid("pfp_idle_input");
        ib = fluid("pfp_order_input");
        oa = fluid("pfp_idle_output");
        ob = fluid("pfp_order_output");
        shared = fluid("pfp_shared_input");
        GTValues.RA.stdBuilder()
            .fluidInputs(new FluidStack(ia, 100), new FluidStack(shared, 100))
            .fluidOutputs(new FluidStack(oa, 100))
            .duration(64)
            .eut(64)
            .metadata(GTRecipeConstants.FUSION_THRESHOLD, 1_000_000L)
            .addTo(RecipeMaps.fusionRecipes);
        GTValues.RA.stdBuilder()
            .fluidInputs(new FluidStack(ib, 100), new FluidStack(shared, 100))
            .fluidOutputs(new FluidStack(ob, 100))
            .duration(64)
            .eut(4096)
            .metadata(GTRecipeConstants.FUSION_THRESHOLD, 200_000_000L)
            .addTo(RecipeMaps.fusionRecipes);
    }

    private static Fluid fluid(String name) {
        Fluid f = new Fluid(name);
        FluidRegistry.registerFluid(f);
        return FluidRegistry.getFluid(name);
    }

    private static final class Cell {

        int x, y, z;
        IStructureElement e;

        Cell(int x, int y, int z, IStructureElement e) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.e = e;
        }
    }

    private static final class Rig {

        int id, stage;
        MTEMultiBlockBase machine;
        IGregTechTileEntity base;
        FusionPowerAccess access;
        List<Cell> cells = new ArrayList<>();
        List<MTEHatchEnergy> energy = new ArrayList<>();
        List<MTEHatchInput> inputs = new ArrayList<>();
        MTEHatchOutput output;
        List<MTEHatchOutput> outputs = new ArrayList<>();
        long previous, eut;
        int duration;
    }

    private Rig build(int id, int ordinal) throws Exception {
        Rig r = new Rig();
        r.id = id;
        int x = 512 + ordinal * 96, y = 128, z = 512;
        for (int cx = (x - 64) >> 4; cx <= (x + 64) >> 4; cx++)
            for (int cz = (z - 64) >> 4; cz <= (z + 64) >> 4; cz++) world.theChunkProviderServer.loadChunk(cx, cz);
        player.setPosition(x + 1, y, z + 1);
        r.base = place(id, x, y, z);
        r.machine = (MTEMultiBlockBase) r.base.getMetaTileEntity();
        r.access = (FusionPowerAccess) r.machine;
        boolean normal = r.machine instanceof MTEFusionComputer;
        String piece = normal ? "main" : "largeFusion";
        if (normal) ((MTEFusionComputer) r.machine).construct(new ItemStack(Items.stick), false);
        else((MTELargeFusionComputer) r.machine).construct(new ItemStack(Items.stick), false);
        IStructureDefinition definition = normal ? ((MTEFusionComputer) r.machine).getStructureDefinition()
            : ((MTELargeFusionComputer) r.machine).getStructure_EM();
        definition.iterate(
            piece,
            world,
            ((MTEEnhancedMultiBlockBase) r.machine).getExtendedFacing(),
            x,
            y,
            z,
            normal ? 7 : 23,
            normal ? 1 : 3,
            normal ? 12 : 40,
            new IStructureWalker() {

                @Override
                public boolean visit(IStructureElement e, World w, int px, int py, int pz, int a, int b, int c) {
                    if (w.getTileEntity(px, py, pz) == null && !w.isAirBlock(px, py, pz))
                        r.cells.add(new Cell(px, py, pz, e));
                    return true;
                }

                @Override
                public boolean blockNotLoaded(IStructureElement e, World w, int px, int py, int pz, int a, int b,
                    int c) {
                    throw new AssertionError("unloaded structure");
                }
            });
        for (int i = 0; i < 4; i++) {
            MTEHatchInput input = (MTEHatchInput) install(r, find(MTEHatchInput.class, 4), false, 1).get(0);
            input.getBaseMetaTileEntity()
                .setColorization((byte) (i < 2 ? -1 : 1));
            r.inputs.add(input);
        }
        r.output = (MTEHatchOutput) install(r, find(MTEHatchOutput.class, 4), false, 1).get(0);
        r.outputs.add(r.output);
        r.outputs.add((MTEHatchOutput) install(r, find(MTEHatchOutput.class, 4), false, 1).get(0));
        int tier = (int) (Math.round(Math.log(r.access.pfp$ratedVoltage() / 8.0) / Math.log(4)));
        for (Object h : install(r, find(MTEHatchEnergy.class, tier), true, normal ? 16 : 32))
            r.energy.add((MTEHatchEnergy) h);
        check(
            r.machine.checkStructure(true, r.base),
            "structure failed " + id + " errors=" + field(r.machine, "structureErrors"));
        r.machine.fixAllIssues();
        row(id + "_native_structure", true, "energy_hatches=" + r.energy.size());
        return r;
    }

    private List<MTEHatch> install(Rig r, int hatch, boolean all, int needed) throws Exception {
        List<MTEHatch> accepted = new ArrayList<>();
        for (Cell c : r.cells) {
            if (world.getTileEntity(c.x, c.y, c.z) != null) continue;
            Block old = world.getBlock(c.x, c.y, c.z);
            int meta = world.getBlockMetadata(c.x, c.y, c.z);
            IGregTechTileEntity tile = place(hatch, c.x, c.y, c.z);
            if (c.e.check(r.machine, world, c.x, c.y, c.z)) {
                accepted.add((MTEHatch) tile.getMetaTileEntity());
                if (!all || accepted.size() == needed) break;
            } else world.setBlock(c.x, c.y, c.z, old, meta, 3);
        }
        check(
            accepted.size() == needed,
            "hatch install " + r.id + " " + hatch + " found=" + accepted.size() + " wanted=" + needed);
        return accepted;
    }

    private int find(Class<?> type, int tier) {
        for (int id = 0; id < GregTechAPI.METATILEENTITIES.length; id++) {
            IMetaTileEntity m = GregTechAPI.METATILEENTITIES[id];
            if (m != null && m.getClass() == type && ((MTEHatch) m).mTier == tier) return id;
        }
        throw new AssertionError("hatch missing " + type + " tier=" + tier);
    }

    private IGregTechTileEntity place(int id, int x, int y, int z) {
        world.setBlockToAir(x, y, z);
        check(
            GregTechAPI.METATILEENTITIES[id].getStackForm(1)
                .tryPlaceItemIntoWorld(player, world, x, y, z, 1, .5f, .5f, .5f),
            "place failed " + id);
        IGregTechTileEntity tile = (IGregTechTileEntity) world.getTileEntity(x, y, z);
        tile.disableWorking();
        return tile;
    }

    private void checks(Rig r) throws Exception {
        PowerSettings settings = new PowerSettings();
        settings.set(-1, PowerLimit.ofEUt(64, 6));
        settings
            .set(1, PowerLimit.ofEUt(r.access.pfp$ratedPower(), PowerLimit.displayTier(r.access.pfp$ratedVoltage())));
        r.access.pfp$applySettings(settings);
        reset(r);
        idle(r, 1);
        check(
            r.machine.checkProcessing()
                .wasSuccessful(),
            "low recipe rejected " + r.id);
        check(eut(r) <= 64 && eut(r) > 0, "low budget exceeded");
        row(r.id + "_low_budget", true, "eut=" + eut(r));
        long running = eut(r);
        int duration = r.machine.mMaxProgresstime;
        PowerSettings changed = settings.copy();
        changed.set(-1, PowerLimit.ofEUt(128, 6));
        r.access.pfp$applySettings(changed);
        check(eut(r) == running && r.machine.mMaxProgresstime == duration, "edit restarted running recipe");
        row(r.id + "_edit_preserves_inflight", true, "");
        r.access.pfp$applySettings(settings);
        NBTTagCompound saved = new NBTTagCompound();
        r.machine.saveNBTData(saved);
        r.access.pfp$applySettings(new PowerSettings());
        r.machine.loadNBTData(saved);
        check(
            r.access.pfp$getSettings()
                .equals(settings),
            "NBT settings lost");
        row(r.id + "_save_load", true, "");
        check(r.access.pfp$applySettings(new PowerSettings()) == 0, "auto changed");
        reset(r);
        idle(r, 1);
        check(
            r.machine.checkProcessing()
                .wasSuccessful(),
            "native auto failed");
        long nativeEut = eut(r);
        int nativeTime = r.machine.mMaxProgresstime;
        PowerSettings full = new PowerSettings();
        full.set(-1, PowerLimit.ofEUt(r.access.pfp$ratedPower(), PowerLimit.displayTier(r.access.pfp$ratedVoltage())));
        r.access.pfp$applySettings(full);
        reset(r);
        idle(r, 1);
        check(
            r.machine.checkProcessing()
                .wasSuccessful(),
            "full failed");
        check(
            eut(r) == nativeEut && r.machine.mMaxProgresstime == nativeTime,
            "full differs from native auto: native=" + nativeEut
                + "/"
                + nativeTime
                + " capped="
                + eut(r)
                + "/"
                + r.machine.mMaxProgresstime);
        row(r.id + "_full_matches_auto", true, "eut=" + eut(r));
        r.access.pfp$applySettings(new PowerSettings());
        reset(r);
        idle(r, 64);
        check(
            r.machine.checkProcessing()
                .wasSuccessful(),
            "native parallel recipe failed");
        long parallelNativeEut = eut(r);
        int parallelNativeTime = r.machine.mMaxProgresstime;
        int parallelNativeOutput = r.machine.mOutputFluids[0].amount;
        r.access.pfp$applySettings(full);
        reset(r);
        idle(r, 64);
        check(
            r.machine.checkProcessing()
                .wasSuccessful(),
            "full parallel recipe failed");
        check(
            eut(r) == parallelNativeEut && r.machine.mMaxProgresstime == parallelNativeTime
                && r.machine.mOutputFluids[0].amount == parallelNativeOutput,
            "native parallel/OC behavior changed");
        row(r.id + "_native_parallel_at_full_budget", true, "eut=" + eut(r) + ", output=" + parallelNativeOutput);
        r.access.pfp$applySettings(settings);
        reset(r);
        idle(r, 64);
        check(
            r.machine.checkProcessing()
                .wasSuccessful() && eut(r) <= 64
                && r.inputs.get(0)
                    .getFluidAmount() == 6300,
            "low cap did not constrain parallels/OC");
        row(r.id + "_low_budget_parallel_control", true, "eut=" + eut(r));
        r.access.pfp$applySettings(new PowerSettings());
        reset(r);
        idle(r, 1);
        order(r, 1);
        check(
            r.machine.checkProcessing()
                .wasSuccessful(),
            "native simultaneous selection failed");
        Fluid selected = r.machine.mOutputFluids[0].getFluid();
        full.set(1, PowerLimit.ofEUt(r.access.pfp$ratedPower(), PowerLimit.displayTier(r.access.pfp$ratedVoltage())));
        r.access.pfp$applySettings(full);
        reset(r);
        idle(r, 1);
        order(r, 1);
        check(
            r.machine.checkProcessing()
                .wasSuccessful(),
            "limited simultaneous selection failed");
        check(r.machine.mOutputFluids[0].getFluid() == selected, "native recipe order changed");
        row(r.id + "_native_recipe_order", true, selected.getName());
        r.access.pfp$applySettings(settings);
        reset(r);
        order(r, 1);
        CheckRecipeResult high = r.machine.checkProcessing();
        boolean capable = r.machine instanceof MTEFusionComputer
            ? ((MTEFusionComputer) r.machine).maxEUStore() >= 200_000_000
            : ((MTELargeFusionComputer) r.machine).maxEUStore() >= 200_000_000;
        check(high.wasSuccessful() == capable, "startup capability changed " + high.getID());
        row(r.id + "_startup_capability", true, "can_run_200M=" + capable);
        reset(r);
        PowerSettings blocked = settings.copy();
        blocked.set(1, PowerLimit.ofEUt(1024, 8));
        r.access.pfp$applySettings(blocked);
        order(r, 1);
        check(
            !r.machine.checkProcessing()
                .wasSuccessful(),
            "over-budget recipe accepted");
        check(
            r.inputs.get(2)
                .getFluidAmount() == 100,
            "rejected recipe consumed inputs");
        row(r.id + "_reject_without_consumption", true, "");
        reset(r);
        r.access.pfp$applySettings(settings);
        new PowerPanel(r.machine).createPowerPanel(
            new PanelSyncManager(new com.cleanroommc.modularui.value.sync.ModularSyncManager(false), false));
        row(r.id + "_panel_constructed", true, "");
        NBTTagCompound copied = ((gregtech.api.interfaces.IDataCopyable) r.machine).getCopiedData(player); // FusionPowerAccess
                                                                                                           // is also
                                                                                                           // added as
                                                                                                           // IDataCopyable
                                                                                                           // on
        // the controller.
        check(
            PowerNbt.read(copied)
                .equals(settings),
            "copy lost settings");
        row(r.id + "_configuration_copy", true, "");
        reset(r);
    }

    private void advance(Rig r) throws Exception {
        if (r.stage == 5) return;
        if (r.stage == 0) {
            reset(r);
            r.base.increaseStoredEnergyUnits(5_000_000, true);
            idle(r, 1);
            r.previous = r.base.getStoredEU();
            r.base.enableWorking();
            r.stage = 1;
            return;
        }
        long stored = r.base.getStoredEU();
        if (r.stage == 1) {
            if (r.machine.mMaxProgresstime > 0) {
                check(r.previous - stored >= 900_000 && r.previous - stored <= 1_000_000, "cold tax incorrect");
                row(r.id + "_native_cold_tax", true, "delta=" + (r.previous - stored));
                r.stage = 2;
            }
            r.previous = stored;
            return;
        }
        if (r.stage == 2 && r.machine.mProgresstime == r.machine.mMaxProgresstime - 1) {
            order(r, 1);
            r.eut = eut(r);
            r.previous = stored;
            r.stage = 3;
            return;
        }
        if (r.stage == 3) {
            check(r.machine.mMaxProgresstime > 0 && r.access.pfp$activeColor() == 1, "hot handoff failed");
            check(r.previous - stored == r.eut, "hot handoff charged startup: " + (r.previous - stored));
            row(
                r.id + "_mk1_to_mk2_hot_switch",
                true,
                "delta=" + (r.previous - stored) + ", stored_below_200M=" + (stored < 200_000_000));
            r.stage = 4;
        }
        if (r.stage == 4 && r.machine.mProgresstime == r.machine.mMaxProgresstime - 1) {
            idle(r, 1);
            r.eut = eut(r);
            r.previous = stored;
            r.stage = 6;
            return;
        }
        if (r.stage == 6) {
            check(r.machine.mMaxProgresstime > 0 && r.access.pfp$activeColor() == -1, "idle handback failed");
            check(r.previous - stored == r.eut, "handback charged startup");
            row(r.id + "_mk2_to_low_idle_hot_switch", true, "delta=" + (r.previous - stored));
            r.base.disableWorking();
            r.machine.stopMachine(ShutDownReasonRegistry.POWER_LOSS);
            r.stage = 5;
        }
    }

    private void reset(Rig r) {
        r.base.disableWorking();
        r.machine.stopMachine(ShutDownReasonRegistry.POWER_LOSS);
        r.machine.mRunningOnLoad = false; // Test reset aborts a restored reaction before native resume checks.
        for (MTEHatchInput h : r.inputs) h.mFluid = null;
        for (MTEHatchOutput output : r.outputs) output.mFluid = null;
    }

    private void idle(Rig r, int crafts) {
        r.inputs.get(0).mFluid = new FluidStack(ia, 100 * crafts);
        r.inputs.get(1).mFluid = new FluidStack(shared, 100 * crafts);
    }

    private void order(Rig r, int crafts) {
        r.inputs.get(2).mFluid = new FluidStack(ib, 100 * crafts);
        r.inputs.get(3).mFluid = new FluidStack(shared, 100 * crafts);
    }

    private long eut(Rig r) throws Exception {
        return -((Number) field(r.machine, r.machine instanceof MTEFusionComputer ? "mEUt" : "lEUt")).longValue();
    }

    private static Object field(Object target, String name) throws Exception {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(name);
    }

    private void row(String name, boolean passed, String detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("passed", passed);
        row.put("detail", detail);
        rows.add(row);
        System.out.println("FUSION_POWER_TEST " + name + " " + passed + " " + detail);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private void finish() throws Exception {
        done = true;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(
            "passed",
            !rows.isEmpty() && rows.stream()
                .allMatch(r -> Boolean.TRUE.equals(r.get("passed"))));
        out.put("tests", rows);
        out.put("client_launched", false);
        Files.write(
            server.getFile("fusion-power-tests.json")
                .toPath(),
            new GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(out)
                .getBytes(StandardCharsets.UTF_8));
        server.initiateShutdown();
    }
}
