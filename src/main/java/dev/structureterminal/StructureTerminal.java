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
    name = "Structure Terminal",
    version = "0.1.2-alpha",
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:structurelib@[1.4.45,);after:gregtech")
public final class StructureTerminal {

    public static final String ID = "structureterminal";
    public static Item item;
    @SidedProxy(
        clientSide = "dev.structureterminal.client.ClientProxy",
        serverSide = "dev.structureterminal.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        TerminalConfig.load(event.getSuggestedConfigurationFile());
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
        Item projector = GameRegistry.findItem("structurelib", "item.structurelib.constructableTrigger");
        if (projector != null) GameRegistry
            .addShapelessRecipe(new ItemStack(item), new ItemStack(projector), Items.ender_pearl, Items.book);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new dev.structureterminal.server.AuditCommand());
    }
}
