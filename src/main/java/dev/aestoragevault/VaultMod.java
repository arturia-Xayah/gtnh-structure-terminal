package dev.aestoragevault;

import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import appeng.api.AEApi;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import dev.aestoragevault.storage.Repositories;
import dev.aestoragevault.ui.CommonProxy;
import dev.aestoragevault.world.TileAccess;
import dev.aestoragevault.world.TileController;
import dev.aestoragevault.world.TileOutput;
import dev.aestoragevault.world.VaultBlock;
import dev.structureterminal.Tags;

@Mod(
    modid = VaultMod.ID,
    name = "GTNH现代化升级 · ME容量仓库",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:structureterminal;required-after:gregtech;required-after:appliedenergistics2")
public final class VaultMod {

    public static final String ID = "aestoragevault";
    public static final Logger LOG = LogManager.getLogger(ID);
    public static Block CASING, CONTROLLER, ACCESS, OUTPUT;
    @Mod.Instance(ID)
    public static VaultMod INSTANCE;
    @SidedProxy(clientSide = "dev.aestoragevault.ui.ClientProxy", serverSide = "dev.aestoragevault.ui.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        CASING = new VaultBlock(0);
        CONTROLLER = new VaultBlock(1);
        ACCESS = new VaultBlock(2);
        OUTPUT = new VaultBlock(3);
        GameRegistry.registerBlock(CASING, "casing");
        GameRegistry.registerBlock(CONTROLLER, "controller");
        GameRegistry.registerBlock(ACCESS, "access");
        GameRegistry.registerBlock(OUTPUT, "output");
        GameRegistry.registerTileEntity(TileController.class, ID + ":controller");
        GameRegistry.registerTileEntity(TileAccess.class, ID + ":access");
        GameRegistry.registerTileEntity(TileOutput.class, ID + ":output");
        FMLCommonHandler.instance()
            .bus()
            .register(Repositories.INSTANCE);
        MinecraftForge.EVENT_BUS.register(Repositories.INSTANCE);
        NetworkRegistry.INSTANCE.registerGuiHandler(this, proxy);
        proxy.init();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ItemStack drive = AEApi.instance()
            .definitions()
            .blocks()
            .drive()
            .maybeStack(1)
            .orNull();
        if (drive == null) throw new IllegalStateException("AE drive missing for vault recipes");
        gregtech.api.enums.GTValues.RA.stdBuilder()
            .itemInputs(
                gregtech.api.enums.ItemList.Hull_LuV.get(1),
                new Object[] { gregtech.api.enums.OrePrefixes.circuit.get(gregtech.api.enums.Materials.LuV), 4 },
                gregtech.api.enums.ItemList.Sensor_LuV.get(2),
                drive)
            .fluidInputs(gregtech.api.enums.Materials.SolderingAlloy.getMolten(576))
            .itemOutputs(new ItemStack(CONTROLLER))
            .duration(600)
            .eut(gregtech.api.enums.TierEU.RECIPE_LuV)
            .addTo(gregtech.api.recipe.RecipeMaps.assemblerRecipes);
        gregtech.api.enums.GTValues.RA.stdBuilder()
            .itemInputs(
                gregtech.api.enums.ItemList.Hull_LuV.get(1),
                new Object[] { gregtech.api.enums.OrePrefixes.circuit.get(gregtech.api.enums.Materials.LuV), 2 },
                gregtech.api.enums.ItemList.Emitter_LuV.get(2),
                drive)
            .fluidInputs(gregtech.api.enums.Materials.SolderingAlloy.getMolten(288))
            .itemOutputs(new ItemStack(ACCESS))
            .duration(400)
            .eut(gregtech.api.enums.TierEU.RECIPE_LuV)
            .addTo(gregtech.api.recipe.RecipeMaps.assemblerRecipes);
        gregtech.api.enums.GTValues.RA.stdBuilder()
            .itemInputs(
                gregtech.api.enums.ItemList.Hull_LuV.get(1),
                new Object[] { gregtech.api.enums.OrePrefixes.circuit.get(gregtech.api.enums.Materials.LuV), 2 },
                gregtech.api.enums.ItemList.Emitter_LuV.get(2),
                AEApi.instance()
                    .definitions()
                    .parts()
                    .exportBus()
                    .maybeStack(1)
                    .get())
            .fluidInputs(gregtech.api.enums.Materials.SolderingAlloy.getMolten(288))
            .itemOutputs(new ItemStack(OUTPUT))
            .duration(400)
            .eut(gregtech.api.enums.TierEU.RECIPE_LuV)
            .addTo(gregtech.api.recipe.RecipeMaps.assemblerRecipes);
        appeng.api.config.Upgrades.FUZZY.registerItem(new ItemStack(OUTPUT), 1);
        appeng.api.config.Upgrades.INVERTER.registerItem(new ItemStack(OUTPUT), 1);
        appeng.api.config.Upgrades.ORE_FILTER.registerItem(new ItemStack(OUTPUT), 1);
    }

    public static boolean isLuVCasing(net.minecraft.world.IBlockAccess world, int x, int y, int z) {
        return world.getBlock(x, y, z) == gregtech.api.GregTechAPI.sBlockCasings1
            && world.getBlockMetadata(x, y, z) == 6;
    }

    @Mod.EventHandler
    public void stop(FMLServerStoppedEvent event) {
        Repositories.INSTANCE.closeAll();
    }

    @NetworkCheckHandler
    public boolean checkVersion(Map<String, String> remote, Side side) {
        return Tags.VERSION.equals(remote.get(ID));
    }
}
