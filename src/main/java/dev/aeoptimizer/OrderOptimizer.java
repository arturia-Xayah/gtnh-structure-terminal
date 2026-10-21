package dev.aeoptimizer;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import dev.aeoptimizer.network.BlockOptimizer;
import dev.aeoptimizer.network.TileOptimizer;
import dev.aeoptimizer.ui.CommonProxy;
import dev.structureterminal.Tags;

@Mod(
    modid = OrderOptimizer.ID,
    name = "AE Order Optimizer",
    version = Tags.VERSION,
    dependencies = "required-after:appliedenergistics2;required-after:unimixins",
    acceptedMinecraftVersions = "[1.7.10]")
public final class OrderOptimizer {

    public static final String ID = "aeorderoptimizer";
    public static final Logger LOG = LogManager.getLogger(ID);
    @Mod.Instance(ID)
    public static OrderOptimizer INSTANCE;
    @SidedProxy(clientSide = "dev.aeoptimizer.ui.ClientProxy", serverSide = "dev.aeoptimizer.ui.CommonProxy")
    public static CommonProxy proxy;
    public static BlockOptimizer BLOCK;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        BLOCK = new BlockOptimizer();
        GameRegistry.registerBlock(BLOCK, "core");
        GameRegistry.registerTileEntity(TileOptimizer.class, ID + ":core");
        NetworkRegistry.INSTANCE.registerGuiHandler(this, proxy);
        GameRegistry.addRecipe(
            new ItemStack(BLOCK),
            "GQG",
            "RCR",
            "GQG",
            'G',
            Items.gold_ingot,
            'Q',
            Items.quartz,
            'R',
            Items.redstone,
            'C',
            Blocks.crafting_table);
    }
}
