package dev.structureterminal;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import dev.structureterminal.bridge.StructureScanner;
import dev.structureterminal.net.TerminalNetwork;
import dev.structureterminal.server.BuildService;

@Mod(
    modid = StructureTerminal.ID,
    name = "GTNH现代化升级",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:structurelib@[1.4.45,);required-after:appliedenergistics2;required-after:unimixins;required-after:gregtech;required-after:modularui2")
public final class StructureTerminal {

    public static final String ID = "structureterminal";
    @Mod.Instance(ID)
    public static StructureTerminal instance;
    public static Item item;
    private java.io.File configFile;
    @SidedProxy(
        clientSide = "dev.structureterminal.client.ClientProxy",
        serverSide = "dev.structureterminal.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        configFile = event.getSuggestedConfigurationFile();
        TerminalConfig.load(configFile);
        dev.structureterminal.packing.PackingItems.register();
        dev.structureterminal.pattern.IntegratedPattern.register();
        FMLCommonHandler.instance()
            .bus()
            .register(dev.structureterminal.pattern.IntegratedPatternOpening.INSTANCE);
        cpw.mods.fml.common.network.NetworkRegistry.INSTANCE.registerGuiHandler(this, proxy);
        item = new TerminalItem();
        GameRegistry.registerItem(item, "structure_terminal");
        TerminalNetwork.init();
        MinecraftForge.EVENT_BUS.register(new StructureScanner());
        FMLCommonHandler.instance()
            .bus()
            .register(BuildService.INSTANCE);
        proxy.init();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        dev.structureterminal.packing.PackingItems.recipe();
        dev.structureterminal.pattern.IntegratedPattern.recipe();
        dev.structureterminal.packing.PackingService.configure(configFile);
        Item projector = GameRegistry.findItem("structurelib", "item.structurelib.constructableTrigger");
        if (projector != null) GameRegistry
            .addShapelessRecipe(new ItemStack(item), new ItemStack(projector), Items.ender_pearl, Items.book);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new dev.structureterminal.server.AuditCommand());
    }
}
