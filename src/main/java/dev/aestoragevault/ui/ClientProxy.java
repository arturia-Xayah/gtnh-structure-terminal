package dev.aestoragevault.ui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import dev.aestoragevault.world.TileController;

public class ClientProxy extends CommonProxy {

    @Override
    public void init() {
        dev.aestoragevault.world.VaultBlock.renderId = cpw.mods.fml.client.registry.RenderingRegistry
            .getNextAvailableRenderId();
        cpw.mods.fml.client.registry.RenderingRegistry
            .registerBlockHandler(new dev.aestoragevault.client.VaultBlockRenderer());
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == 1 && world.getTileEntity(x, y, z) instanceof dev.aestoragevault.world.TileOutput)
            return new OutputScreen(player, (dev.aestoragevault.world.TileOutput) world.getTileEntity(x, y, z));
        return world.getTileEntity(x, y, z) instanceof TileController
            ? new VaultScreen(player, (TileController) world.getTileEntity(x, y, z))
            : null;
    }
}
