package dev.aeoptimizer.ui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import dev.aeoptimizer.network.TileOptimizer;

public class ClientProxy extends CommonProxy {

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return world.getTileEntity(x, y, z) instanceof TileOptimizer
            ? new OptimizerScreen(new OptimizerContainer((TileOptimizer) world.getTileEntity(x, y, z)))
            : null;
    }
}
