package dev.plasmaforgepower.packtest;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.plasmaforgepower.api.ForgePowerAccess;
import dev.plasmaforgepower.core.PowerLimit;
import dev.plasmaforgepower.core.PowerNbt;
import dev.plasmaforgepower.core.PowerSettings;
import dev.plasmaforgepower.ui.ForgePowerGui;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.IDataCopyable;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTUtil;
import gregtech.common.tileentities.machines.multi.MTEPlasmaForge;
import io.netty.channel.embedded.EmbeddedChannel;
import tectech.thing.metaTileEntity.hatch.MTEHatchEnergyMulti;

/** Dedicated isolated full-pack tests, excluded from the installable JAR. */
@Mod(
    modid = "forgepowertests",
    name = "Forge Power server tests",
    version = "1",
    dependencies = "required-after:gregtech;required-after:plasmaforgepower")
public final class ForgePowerSuite {

    private static final int X = 256, Y = 128, Z = 256;
    private MinecraftServer server;
    private WorldServer world;
    private FakePlayer player;
    private MTEPlasmaForge forge;
    private IGregTechTileEntity base;
    private ForgePowerAccess access;
    private MTEHatchEnergyMulti energy;
    private MTEHatchInputBus red, green, plain;
    private MTEHatchInput water;
    private final List<int[]> casing = new ArrayList<>();
    private final List<Map<String, Object>> results = new ArrayList<>();
    private int ticks;
    private boolean started;
    private long lowPower, highPower;
    private int lowDuration;
    private boolean tickRun;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (Boolean.getBoolean("plasmaforgepower.tests") && FMLCommonHandler.instance()
            .getSide()
            .isServer()) {
            FMLCommonHandler.instance()
                .bus()
                .register(this);
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || started && !tickRun) return;
        try {
            if (!started) {
                started = true;
                setup();
                runTests();
                if (results.stream()
                    .anyMatch(row -> !Boolean.TRUE.equals(row.get("passed")))) {
                    finish();
                    return;
                }
                clearInputs();
                PowerSettings settings = new PowerSettings();
                settings.set(1, PowerLimit.parse("1", 0));
                access.pfp$applySettings(settings);
                red.setInventorySlotContents(0, new ItemStack(Items.apple));
                forge.setConvergenceStatus(false);
                check(
                    forge.checkProcessing()
                        .wasSuccessful(),
                    "tick recipe failed to start");
                lowDuration = forge.mMaxProgresstime;
                forge.fixAllIssues();
                fillEnergy();
                base.enableWorking();
                tickRun = true;
                ticks = 0;
            } else {
                ticks++;
                if (forge.mProgresstime > 0 || base.isActive()) {
                    if (-forge.lEUt > PowerLimit.voltage(0)) throw new AssertionError("tick exceeded color power cap");
                }
                if (ticks > 5 && outputCount(Items.brick) > 0) {
                    record("native_tick_recipe_completed", true, "ticks=" + ticks + ", recipe_duration=" + lowDuration);
                    tickRun = false;
                    persistFinal();
                    finish();
                } else if (ticks > lowDuration + 300) {
                    record(
                        "native_tick_recipe_completed",
                        false,
                        "timeout progress=" + forge.mProgresstime + " active=" + base.isActive());
                    tickRun = false;
                    finish();
                }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            record("harness", false, failure.toString());
            tickRun = false;
            try {
                finish();
            } catch (Exception nested) {
                nested.printStackTrace();
                if (server != null) server.initiateShutdown();
            }
        }
    }

    private void setup() throws Exception {
        server = MinecraftServer.getServer();
        world = server.worldServerForDimension(0);
        check(
            server.isDedicatedServer() && world.getWorldInfo()
                .getWorldName()
                .equals("forge-power-validation"),
            "wrong world");
        for (int cx = 12; cx <= 23; cx++)
            for (int cz = 12; cz <= 21; cz++) world.theChunkProviderServer.loadChunk(cx, cz);
        world.getGameRules()
            .setOrCreateGameRule("doMobSpawning", "false");
        player = new FakePlayer(
            world,
            new GameProfile(UUID.fromString("ae000000-0000-4000-8000-000000000021"), "ForgePowerTest"));
        NetworkManager wire = new NetworkManager(false);
        new EmbeddedChannel(wire);
        new NetHandlerPlayServer(server, wire, player) {

            @Override
            public void sendPacket(Packet packet) {}
        };
        player.setPosition(X + 2.5, Y, Z + 2.5);
        player.capabilities.allowEdit = true;
        server.getConfigurationManager().playerEntityList.add(player);
        world.playerEntities.add(player);
        base = place(1004, X, Y, Z);
        forge = (MTEPlasmaForge) base.getMetaTileEntity();
        access = (ForgePowerAccess) forge;
        base.disableWorking();
        forge.construct(new ItemStack(Items.stick), false);
        for (int x = X - 35; x <= X + 35; x++)
            for (int y = Y - 30; y <= Y + 30; y++) for (int z = Z - 35; z <= Z + 35; z++) {
                if (world.getBlock(x, y, z) == GregTechAPI.sBlockCasings1 && world.getBlockMetadata(x, y, z) == 13)
                    casing.add(new int[] { x, y, z });
            }
        check(casing.size() > 20, "native construction did not create injection casings: " + casing.size());
        energy = (MTEHatchEnergyMulti) placeAt(findEnergy(1024), 0).getMetaTileEntity();
        red = (MTEHatchInputBus) placeAt(findHatch(MTEHatchInputBus.class), 1).getMetaTileEntity();
        green = (MTEHatchInputBus) placeAt(findHatch(MTEHatchInputBus.class), 2).getMetaTileEntity();
        plain = (MTEHatchInputBus) placeAt(findHatch(MTEHatchInputBus.class), 3).getMetaTileEntity();
        water = (MTEHatchInput) placeAt(findHatch(MTEHatchInput.class), 4).getMetaTileEntity();
        placeAt(findHatch(MTEHatchOutputBus.class), 5);
        placeAt(findHatch(MTEHatchOutput.class), 6);
        red.getBaseMetaTileEntity()
            .setColorization((byte) 1);
        green.getBaseMetaTileEntity()
            .setColorization((byte) 2);
        check(forge.checkStructure(true, base), "native structure failed: " + nativeErrors());
        forge.fixAllIssues();
        fillEnergy();
        GTValues.RA.stdBuilder()
            .itemInputs(new ItemStack(Items.apple))
            .itemOutputs(new ItemStack(Items.brick))
            .duration(400)
            .eut(8388608)
            .specialValue(0)
            .addTo(RecipeMaps.plasmaForgeRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(new ItemStack(Items.sugar))
            .itemOutputs(new ItemStack(Items.flint))
            .duration(400)
            .eut(33554432)
            .specialValue(0)
            .addTo(RecipeMaps.plasmaForgeRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(new ItemStack(Items.carrot), new ItemStack(Items.potato))
            .itemOutputs(new ItemStack(Items.stick))
            .duration(400)
            .eut(8388608)
            .specialValue(0)
            .addTo(RecipeMaps.plasmaForgeRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(new ItemStack(Items.feather))
            .fluidInputs(new FluidStack(FluidRegistry.WATER, 100))
            .itemOutputs(new ItemStack(Items.clay_ball))
            .duration(400)
            .eut(8388608)
            .specialValue(0)
            .addTo(RecipeMaps.plasmaForgeRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(new ItemStack(Items.bone))
            .fluidInputs(Materials.ExcitedDTCC.getFluid(10000))
            .itemOutputs(new ItemStack(Items.beef))
            .duration(400)
            .eut(8388608)
            .specialValue(0)
            .addTo(RecipeMaps.plasmaForgeRecipes);
        GTValues.RA.stdBuilder()
            .fluidInputs(new FluidStack(FluidRegistry.LAVA, 100))
            .fluidOutputs(new FluidStack(FluidRegistry.WATER, 50))
            .duration(400)
            .eut(8388608)
            .specialValue(0)
            .addTo(RecipeMaps.plasmaForgeRecipes);
        record(
            "native_full_structure_formed",
            true,
            "supply=" + access.pfp$ratedPower() + ", casings=" + casing.size());
    }

    private void runTests() throws Exception {
        run("target_only_and_native_controller", () -> {
            check(forge instanceof IDataCopyable, "missing copy interface");
            check(!(GregTechAPI.METATILEENTITIES[1000] instanceof ForgePowerAccess), "unrelated controller changed");
            check(base.getMetaTileID() == 1004, "controller identity changed");
        });
        run("native_ui_panel_constructs_on_server", () -> {
            PanelSyncManager manager = new PanelSyncManager(new ModularSyncManager(false), false);
            check(
                new ForgePowerGui(forge).createPowerPanel(manager)
                    .getChildren()
                    .size() > 35,
                "missing panel controls");
        });
        run("nbt_roundtrip_all_groups_and_large_values", () -> {
            PowerSettings settings = new PowerSettings();
            for (int color = -1; color < 16; color++)
                settings.set(color, PowerLimit.ofEUt(Long.MAX_VALUE - color - 20, (color + 1) % 5));
            check(settings.equals(PowerNbt.read(PowerNbt.write(settings))), "NBT lost precision");
        });
        run("clipboard_rejects_foreign_payload_atomically", () -> {
            PowerSettings before = access.pfp$getSettings();
            check(!((IDataCopyable) forge).pasteCopiedData(null, new NBTTagCompound()), "invalid accepted");
            check(before.equals(access.pfp$getSettings()), "invalid clipboard mutated settings");
        });
        run("world_nbt_keeps_configuration_without_formation", () -> {
            PowerSettings settings = configured();
            access.pfp$applySettings(settings);
            NBTTagCompound tag = new NBTTagCompound();
            forge.saveNBTData(tag);
            MTEPlasmaForge copy = (MTEPlasmaForge) place(1004, X + 68, Y, Z).getMetaTileEntity();
            copy.loadNBTData(tag);
            check(settings.equals(((ForgePowerAccess) copy).pfp$getSettings()), "save/load changed limits");
        });
        run("matter_manipulator_actual_analyze_and_apply", this::testManipulator);
        run("matter_manipulator_numeric_width_and_long_precision", () -> {
            PowerSettings settings = new PowerSettings();
            settings.set(1, PowerLimit.ofEUt(Long.MAX_VALUE - 7, 4));
            Class<?> utils = Class.forName("com.recursive_pineapple.matter_manipulator.common.utils.MMUtils");
            Object json = utils.getMethod("toJsonObject", net.minecraft.nbt.NBTBase.class)
                .invoke(null, PowerNbt.write(settings));
            NBTTagCompound restored = (NBTTagCompound) utils.getMethod("toNbt", com.google.gson.JsonElement.class)
                .invoke(null, json);
            check(settings.equals(PowerNbt.read(restored)), "MM conversion lost configuration or precision");
        });
        run("native_data_orb_copy_paste", () -> {
            access.pfp$applySettings(configured());
            player.inventory.setInventorySlotContents(0, ItemList.Tool_DataOrb.get(1));
            player.inventory.currentItem = 0;
            check(GTUtil.saveMultiblockInputConfiguration(forge, player), "orb save failed");
            check(
                player.getHeldItem()
                    .getTagCompound()
                    .hasKey(PowerNbt.KEY),
                "orb lost custom settings");
            access.pfp$applySettings(new PowerSettings());
            check(GTUtil.loadMultiblockInputConfiguration(forge, player), "orb paste failed");
            check(configured().equals(access.pfp$getSettings()), "orb changed configuration");
        });
        run("red_group_low_power_recipe", () -> {
            clearInputs();
            access.pfp$applySettings(configured());
            red.setInventorySlotContents(0, new ItemStack(Items.apple));
            CheckRecipeResult result = forge.checkProcessing();
            check(result.wasSuccessful(), result.toString());
            lowPower = -forge.lEUt;
            lowDuration = forge.mMaxProgresstime;
            check(lowPower > 0 && lowPower <= PowerLimit.voltage(0), "red used extra power: " + lowPower);
            check(access.pfp$activeColor() == 1, "wrong selected color");
            check(
                access.pfp$attemptPower() == -1 && forge.getMaxInputEu() == access.pfp$ratedPower(),
                "power context leaked");
        });
        run("green_group_uses_own_larger_power", () -> {
            clearInputs();
            green.setInventorySlotContents(0, new ItemStack(Items.apple));
            check(
                forge.checkProcessing()
                    .wasSuccessful(),
                "green recipe failed");
            highPower = -forge.lEUt;
            check(highPower > lowPower && highPower <= 16 * PowerLimit.voltage(2), "green budget wrong: " + highPower);
            check(forge.mMaxProgresstime < lowDuration, "green did not overclock");
        });
        run("over_budget_recipe_keeps_ingredients_and_checks_next_group", () -> {
            clearInputs();
            red.setInventorySlotContents(0, new ItemStack(Items.sugar));
            green.setInventorySlotContents(0, new ItemStack(Items.apple));
            check(
                forge.checkProcessing()
                    .wasSuccessful(),
                "other input group was blocked");
            check(
                red.getStackInSlot(0) != null && red.getStackInSlot(0).stackSize == 1,
                "rejected recipe consumed ingredients");
            check(access.pfp$activeColor() == 2, "did not continue to green");
        });
        run("different_colors_never_merge_ingredients", () -> {
            clearInputs();
            red.setInventorySlotContents(0, new ItemStack(Items.carrot));
            green.setInventorySlotContents(0, new ItemStack(Items.potato));
            check(
                !forge.checkProcessing()
                    .wasSuccessful(),
                "cross-color recipe started");
            check(
                red.getStackInSlot(0).stackSize == 1 && green.getStackInSlot(0).stackSize == 1,
                "cross-color ingredients consumed");
        });
        run("unpainted_auto_matches_native_processing_power", () -> {
            clearInputs();
            access.pfp$applySettings(new PowerSettings());
            plain.setInventorySlotContents(0, new ItemStack(Items.apple));
            check(
                forge.checkProcessing()
                    .wasSuccessful(),
                "native automatic recipe failed");
            long nativePower = -forge.lEUt;
            int nativeTime = forge.mMaxProgresstime;
            clearInputs();
            access.pfp$applySettings(configured());
            plain.setInventorySlotContents(0, new ItemStack(Items.apple));
            check(
                forge.checkProcessing()
                    .wasSuccessful(),
                "unpainted automatic recipe failed");
            check(nativePower == -forge.lEUt && nativeTime == forge.mMaxProgresstime, "automatic diverged from native");
            check(access.pfp$activeColor() == -1, "unpainted inherited painted budget");
        });
        run("unpainted_manual_cap_cannot_escape_via_empty_painted_bus", () -> {
            clearInputs();
            PowerSettings settings = new PowerSettings();
            settings.set(-1, PowerLimit.parse("1", 0));
            access.pfp$applySettings(settings);
            plain.setInventorySlotContents(0, new ItemStack(Items.sugar));
            check(
                !forge.checkProcessing()
                    .wasSuccessful(),
                "unpainted recipe escaped to an unlimited painted bus");
            check(plain.getStackInSlot(0).stackSize == 1, "unpainted limit consumed ingredients");
        });
        run("complete_painted_recipe_can_run_without_taking_unpainted_items", () -> {
            clearInputs();
            red.setInventorySlotContents(0, new ItemStack(Items.sugar));
            plain.setInventorySlotContents(0, new ItemStack(Items.sugar));
            check(
                forge.checkProcessing()
                    .wasSuccessful(),
                "complete painted recipe was blocked");
            check(access.pfp$activeColor() == 1, "wrong source group");
            check(plain.getStackInSlot(0).stackSize == 1, "painted recipe stole unpainted ingredients");
        });
        run("input_separation_and_shared_items_keep_native_semantics", () -> {
            clearInputs();
            access.pfp$applySettings(configured());
            forge.setInputSeparation(true);
            red.setInventorySlotContents(0, new ItemStack(Items.carrot));
            plain.setInventorySlotContents(0, new ItemStack(Items.potato));
            check(
                !forge.checkProcessing()
                    .wasSuccessful(),
                "separated buses were merged");
            forge.setInputSeparation(false);
            check(
                forge.checkProcessing()
                    .wasSuccessful(),
                "shared unpainted item could not supplement red");
            check(-forge.lEUt <= PowerLimit.voltage(0), "shared item bypassed red power");
        });
        run("colored_fluid_only_recipe_uses_hatch_color", () -> {
            clearInputs();
            water.getBaseMetaTileEntity()
                .setColorization((byte) 1);
            water.fill(ForgeDirection.UNKNOWN, new FluidStack(FluidRegistry.LAVA, 100), true);
            check(
                forge.checkProcessing()
                    .wasSuccessful(),
                "fluid-only recipe failed");
            check(access.pfp$activeColor() == 1 && -forge.lEUt <= PowerLimit.voltage(0), "wrong fluid power group");
            water.getBaseMetaTileEntity()
                .setColorization((byte) -1);
        });
        run("unpainted_shared_fluid_does_not_remove_red_limit", () -> {
            clearInputs();
            red.setInventorySlotContents(0, new ItemStack(Items.feather));
            water.fill(ForgeDirection.UNKNOWN, new FluidStack(FluidRegistry.WATER, 100), true);
            check(
                forge.checkProcessing()
                    .wasSuccessful(),
                "shared fluid recipe failed");
            check(-forge.lEUt <= PowerLimit.voltage(0), "shared fluid bypassed red cap");
        });
        run("settings_change_does_not_recalculate_current_recipe", () -> {
            long eut = forge.lEUt;
            int duration = forge.mMaxProgresstime;
            access.pfp$applySettings(new PowerSettings());
            check(eut == forge.lEUt && duration == forge.mMaxProgresstime, "current recipe changed");
        });
        run("convergence_catalyst_and_overclock_use_color_budget", () -> {
            clearInputs();
            access.pfp$applySettings(configured());
            Field runtime = MTEPlasmaForge.class.getDeclaredField("running_time");
            runtime.setAccessible(true);
            runtime.setLong(forge, 576000L);
            forge.setConvergenceStatus(true);
            red.setInventorySlotContents(0, new ItemStack(Items.bone));
            water.fill(ForgeDirection.UNKNOWN, Materials.ExcitedDTCC.getFluid(100000), true);
            check(
                forge.checkProcessing()
                    .wasSuccessful(),
                "low-power convergence failed");
            int lowCatalyst = 100000 - water.getFillableStack().amount;
            check(-forge.lEUt <= PowerLimit.voltage(0), "convergence bypassed red limit");
            clearInputs();
            green.setInventorySlotContents(0, new ItemStack(Items.bone));
            water.fill(ForgeDirection.UNKNOWN, Materials.ExcitedDTCC.getFluid(100000), true);
            check(
                forge.checkProcessing()
                    .wasSuccessful(),
                "high-power convergence failed");
            int highCatalyst = 100000 - water.getFillableStack().amount;
            check(highCatalyst > lowCatalyst, "catalyst adjustment ignored selected budget");
            check(-forge.lEUt <= 16 * PowerLimit.voltage(2), "convergence bypassed green limit");
            forge.setConvergenceStatus(false);
        });
        run("native_running_tick_draws_actual_eut", () -> {
            fillEnergy();
            long before = energy.getBaseMetaTileEntity()
                .getStoredEU();
            check(forge.onRunningTick(null), "native tick power failure");
            long used = before - energy.getBaseMetaTileEntity()
                .getStoredEU();
            check(used == -forge.lEUt, "wrong actual drain: " + used + " vs " + (-forge.lEUt));
        });
        run("partial_structure_preserves_configuration_until_success", () -> {
            access.pfp$applySettings(configured());
            int[] broken = casing.get(10);
            Block old = world.getBlock(broken[0], broken[1], broken[2]);
            int meta = world.getBlockMetadata(broken[0], broken[1], broken[2]);
            world.setBlockToAir(broken[0], broken[1], broken[2]);
            energy = (MTEHatchEnergyMulti) placeAt(findEnergy(4), 0).getMetaTileEntity();
            check(!forge.checkStructure(true, base), "broken structure formed");
            check(
                access.pfp$getSettings()
                    .get(2)
                    .equals(configured().get(2)),
                "incomplete structure erased config");
            world.setBlock(broken[0], broken[1], broken[2], old, meta, 2);
            check(forge.checkStructure(true, base), "repaired structure failed");
            check(
                access.pfp$getSettings()
                    .get(2)
                    .eut() == 4 * PowerLimit.voltage(2),
                "reformed lower supply did not clamp");
            check(
                access.pfp$getSettings()
                    .get(-1)
                    .isAuto(),
                "auto became manual");
        });
        run("larger_replacement_does_not_restore_old_limit", () -> {
            energy = (MTEHatchEnergyMulti) placeAt(findEnergy(1024), 0).getMetaTileEntity();
            check(forge.checkStructure(true, base), "larger supply did not form");
            check(
                access.pfp$getSettings()
                    .get(2)
                    .eut() == 4 * PowerLimit.voltage(2),
                "manual limit grew again");
        });
        run("clamp_at_paste_into_smaller_formed_forge", () -> {
            energy = (MTEHatchEnergyMulti) placeAt(findEnergy(4), 0).getMetaTileEntity();
            check(forge.checkStructure(true, base), "lower supply did not form");
            check(((IDataCopyable) forge).pasteCopiedData(null, PowerNbt.write(configured())), "paste rejected");
            check(
                access.pfp$getSettings()
                    .get(2)
                    .eut() == 4 * PowerLimit.voltage(2),
                "paste exceeded supply");
        });
    }

    private void testManipulator() throws Exception {
        access.pfp$applySettings(configured());
        Class<?> analysis = Class
            .forName("com.recursive_pineapple.matter_manipulator.common.building.GTAnalysisResult");
        Object captured = analysis.getMethod("analyze", TileEntity.class)
            .invoke(null, world.getTileEntity(X, Y, Z));
        check(
            captured != null && analysis.getField("mGTData")
                .get(captured) != null,
            "MM did not capture settings");
        IGregTechTileEntity target = place(1004, X + 64, Y, Z);
        Class<?> context = Class
            .forName("com.recursive_pineapple.matter_manipulator.common.building.BlockAnalyzer$IBlockApplyContext");
        Object proxy = Proxy
            .newProxyInstance(context.getClassLoader(), new Class<?>[] { context }, (object, method, args) -> {
                switch (method.getName()) {
                    case "getWorld":
                        return world;
                    case "getX":
                        return X + 64;
                    case "getY":
                        return Y;
                    case "getZ":
                        return Z;
                    case "getTileEntity":
                        return world.getTileEntity(X + 64, Y, Z);
                    case "getRealPlayer":
                        return player;
                    case "tryConsumeItems":
                        return true;
                    case "error":
                        throw new AssertionError("MM error: " + java.util.Arrays.toString(args));
                    case "hashCode":
                        return System.identityHashCode(object);
                    case "equals":
                        return object == args[0];
                    default:
                        return method.getReturnType() == boolean.class ? false : null;
                }
            });
        check(
            Boolean.TRUE.equals(
                analysis.getMethod("apply", context)
                    .invoke(captured, proxy)),
            "MM apply failed");
        check(
            configured().equals(((ForgePowerAccess) target.getMetaTileEntity()).pfp$getSettings()),
            "MM lost config on incomplete target");
        check(!((ForgePowerAccess) target.getMetaTileEntity()).pfp$isFormed(), "target unexpectedly formed");
        target.disableWorking();
    }

    private void persistFinal() {
        access.pfp$applySettings(configured());
        base.disableWorking();
        if (base.getMetaTileEntity() != forge) throw new AssertionError("controller changed");
        base.markDirty();
    }

    private PowerSettings configured() {
        PowerSettings settings = new PowerSettings();
        settings.set(1, PowerLimit.parse("1", 0));
        settings.set(2, PowerLimit.parse("16", 2));
        return settings;
    }

    private void clearInputs() {
        base.disableWorking();
        forge.lEUt = 0;
        forge.mProgresstime = 0;
        forge.mMaxProgresstime = 0;
        forge.mOutputItems = null;
        forge.mOutputFluids = null;
        for (MTEHatchInputBus bus : new MTEHatchInputBus[] { red, green, plain })
            for (int slot = 0; slot < bus.getSizeInventory(); slot++) bus.setInventorySlotContents(slot, null);
        water.drain(ForgeDirection.UNKNOWN, Integer.MAX_VALUE, true);
        forge.fixAllIssues();
        forge.mEfficiency = 10000;
        fillEnergy();
    }

    private void fillEnergy() {
        energy.getBaseMetaTileEntity()
            .increaseStoredEnergyUnits(energy.maxEUStore(), false);
    }

    private int outputCount(net.minecraft.item.Item item) {
        int count = 0;
        for (MTEHatchOutputBus bus : forge.mOutputBusses) for (int i = 0; i < bus.getSizeInventory(); i++) {
            ItemStack stack = bus.getStackInSlot(i);
            if (stack != null && stack.getItem() == item) count += stack.stackSize;
        }
        return count;
    }

    private IGregTechTileEntity placeAt(int id, int index) throws Exception {
        int[] p = casing.get(index);
        return place(id, p[0], p[1], p[2]);
    }

    private IGregTechTileEntity place(int id, int x, int y, int z) throws Exception {
        world.setBlockToAir(x, y, z);
        ItemStack item = GregTechAPI.METATILEENTITIES[id].getStackForm(1);
        check(item.tryPlaceItemIntoWorld(player, world, x, y, z, 1, .5f, .5f, .5f), "could not place meta " + id);
        IGregTechTileEntity tile = (IGregTechTileEntity) world.getTileEntity(x, y, z);
        tile.disableWorking();
        return tile;
    }

    private int findEnergy(int amps) {
        for (int i = 0; i < GregTechAPI.METATILEENTITIES.length; i++) {
            IMetaTileEntity meta = GregTechAPI.METATILEENTITIES[i];
            if (meta instanceof MTEHatchEnergyMulti) {
                MTEHatchEnergyMulti hatch = (MTEHatchEnergyMulti) meta;
                if (hatch.mTier == 12 && hatch.maxAmperes == amps) return i;
            }
        }
        throw new AssertionError("no UMV " + amps + "A hatch");
    }

    private int findHatch(Class<? extends MTEHatch> type) {
        for (int i = 0; i < GregTechAPI.METATILEENTITIES.length; i++) {
            IMetaTileEntity meta = GregTechAPI.METATILEENTITIES[i];
            if (meta != null && meta.getClass() == type && ((MTEHatch) meta).mTier == 4) return i;
        }
        throw new AssertionError("no hatch: " + type);
    }

    private Object nativeErrors() throws Exception {
        for (Class<?> type = forge.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField("structureErrors");
                field.setAccessible(true);
                return field.get(forge);
            } catch (NoSuchFieldException ignored) {}
        }
        return "unknown";
    }

    private interface Test {

        void run() throws Exception;
    }

    private void run(String name, Test test) {
        try {
            test.run();
            record(name, true, "");
        } catch (Throwable error) {
            error.printStackTrace();
            record(name, false, error.toString());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private void record(String name, boolean passed, String detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("passed", passed);
        row.put("detail", detail);
        results.add(row);
        System.out.println("FORGE_POWER_TEST " + name + " " + passed + " " + detail);
    }

    private void finish() throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(
            "passed",
            !results.isEmpty() && results.stream()
                .allMatch(row -> Boolean.TRUE.equals(row.get("passed"))));
        report.put("tests", results);
        report.put("low_eut", lowPower);
        report.put("green_eut", highPower);
        report.put("client_launched", false);
        Path path = server.getFile("forge-power-tests.json")
            .toPath();
        Files.write(
            path,
            new GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(report)
                .getBytes(StandardCharsets.UTF_8));
        server.initiateShutdown();
    }
}
