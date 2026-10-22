package dev.aestoragevault.ui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.IGuiHandler;
import dev.aestoragevault.world.TileController;

public class CommonProxy implements IGuiHandler {

    public void init() {}

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return world.getTileEntity(x, y, z) instanceof TileController
            ? new VaultContainer(player, (TileController) world.getTileEntity(x, y, z))
            : null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }
}
