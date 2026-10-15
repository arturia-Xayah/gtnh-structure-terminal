package dev.structureterminal;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import dev.structureterminal.bridge.NativeBridge;

public final class TerminalItem extends Item {

    public TerminalItem() {
        setUnlocalizedName("structureterminal.terminal");
        setTextureName("minecraft:map_filled");
        setCreativeTab(CreativeTabs.tabTools);
        setMaxStackSize(1);
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hx, float hy, float hz) {
        if (NativeBridge.resolve(world, x, y, z, side) == null) return false;
        if (world.isRemote) StructureTerminal.proxy.open(player, x, y, z, side);
        return !world.isRemote;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(StatCollector.translateToLocal("structureterminal.tooltip"));
        lines.add(StatCollector.translateToLocal("structureterminal.alpha"));
    }
}
