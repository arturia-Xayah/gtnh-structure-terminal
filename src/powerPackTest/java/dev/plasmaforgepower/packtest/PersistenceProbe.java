package dev.plasmaforgepower.packtest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import com.google.gson.GsonBuilder;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.interfaces.IDataCopyable;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.tileentities.machines.multi.MTEPlasmaForge;

/** Deliberately has no binary link to the add-on, so this class can also test a world after its removal. */
@Mod(
    modid = "forgepowerprobe",
    name = "Forge Power persistence probe",
    version = "1",
    dependencies = "required-after:gregtech")
public final class PersistenceProbe {

    private boolean done;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (!System.getProperty("plasmaforgepower.probe", "")
            .isEmpty() && FMLCommonHandler.instance()
                .getSide()
                .isServer())
            FMLCommonHandler.instance()
                .bus()
                .register(this);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) throws Exception {
        if (done || event.phase != TickEvent.Phase.END) return;
        done = true;
        MinecraftServer server = MinecraftServer.getServer();
        String mode = System.getProperty("plasmaforgepower.probe");
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("mode", mode);
        report.put("client_launched", false);
        try {
            WorldServer world = server.worldServerForDimension(0);
            check(
                server.isDedicatedServer() && world.getWorldInfo()
                    .getWorldName()
                    .equals("forge-power-validation"),
                "wrong server/world");
            for (int x = 12; x <= 23; x++) for (int z = 12; z <= 21; z++) world.theChunkProviderServer.loadChunk(x, z);
            IGregTechTileEntity base = (IGregTechTileEntity) world.getTileEntity(256, 128, 256);
            check(base != null && base.getMetaTileID() == 1004, "controller did not survive save/load");
            MTEPlasmaForge forge = (MTEPlasmaForge) base.getMetaTileEntity();
            base.disableWorking();
            check(forge.checkStructure(true, base), "saved structure no longer forms");
            if (mode.equals("reload")) {
                Object settings = forge.getClass()
                    .getMethod("pfp$getSettings")
                    .invoke(forge);
                check(eut(settings, 1) == 8388608L, "red setting did not survive restart");
                check(eut(settings, 2) == 536870912L, "green clamp did not survive restart");
                check(eut(settings, -1) == -1, "automatic did not survive restart");
                IGregTechTileEntity copy = (IGregTechTileEntity) world.getTileEntity(320, 128, 256);
                Object copied = copy.getMetaTileEntity()
                    .getClass()
                    .getMethod("pfp$getSettings")
                    .invoke(copy.getMetaTileEntity());
                check(eut(copied, 2) == 2147483648L, "incomplete copied target lost settings on restart");
                report.put("red_eut", eut(settings, 1));
                report.put("green_eut", eut(settings, 2));
                report.put("copied_target_green_eut", eut(copied, 2));
            } else if (mode.equals("removed")) {
                check(!(forge instanceof IDataCopyable), "add-on mixin still present after removal");
                NBTTagCompound saved = new NBTTagCompound();
                forge.saveNBTData(saved);
                check(!saved.hasKey("plasmaforgepower:limits"), "original forge still writes add-on settings");
                report.put(
                    "original_controller",
                    forge.getClass()
                        .getName());
                report.put("rated_power", forge.getMaxInputEu());
                report.put("structure_forms", true);
                report.put("custom_settings_ignored", true);
            } else throw new AssertionError("unknown probe mode");
            report.put("passed", true);
        } catch (Throwable error) {
            error.printStackTrace();
            report.put("passed", false);
            report.put("error", error.toString());
        }
        Files.write(
            server.getFile("forge-power-" + mode + ".json")
                .toPath(),
            new GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(report)
                .getBytes(StandardCharsets.UTF_8));
        server.initiateShutdown();
    }

    private static long eut(Object settings, int color) throws Exception {
        Object limit = settings.getClass()
            .getMethod("get", int.class)
            .invoke(settings, color);
        return ((Number) limit.getClass()
            .getMethod("eut")
            .invoke(limit)).longValue();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
