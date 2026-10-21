package dev.aeoptimizer.ui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.IGuiHandler;
import dev.aeoptimizer.network.TileOptimizer;

public class CommonProxy implements IGuiHandler {

    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return world.getTileEntity(x, y, z) instanceof TileOptimizer
            ? new OptimizerContainer((TileOptimizer) world.getTileEntity(x, y, z))
            : null;
    }

    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }
}
