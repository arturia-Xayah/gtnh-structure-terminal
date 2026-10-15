package dev.structureterminal;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

public class CommonProxy {

    public void init() {}

    public void open(EntityPlayer player, int x, int y, int z, int side) {}

    public void receive(NBTTagCompound tag) {}
}
