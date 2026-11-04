package dev.structureterminal;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

public class CommonProxy implements cpw.mods.fml.common.network.IGuiHandler {

    public Object getServerGuiElement(int id, EntityPlayer player, net.minecraft.world.World world, int x, int y,
        int z) {
        return id == dev.structureterminal.pattern.IntegratedPattern.GUI
            && dev.structureterminal.pattern.IntegratedPatternContainer.canOpen(player, x)
                ? new dev.structureterminal.pattern.IntegratedPatternContainer(player, x)
                : null;
    }

    public Object getClientGuiElement(int id, EntityPlayer player, net.minecraft.world.World world, int x, int y,
        int z) {
        return null;
    }

    public void init() {}

    public void open(EntityPlayer player, int x, int y, int z, int side) {}

    public void use(EntityPlayer player, int x, int y, int z, int side, boolean build) {}

    public void settings(EntityPlayer player) {}

    public void receive(NBTTagCompound tag) {}
}
