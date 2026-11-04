package dev.structureterminal;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

public class CommonProxy {

    public void init() {}

    public void open(EntityPlayer player, int x, int y, int z, int side) {}

    public void use(EntityPlayer player, int x, int y, int z, int side, boolean build) {}

    public void settings(EntityPlayer player) {}

    public void receive(NBTTagCompound tag) {}
}
