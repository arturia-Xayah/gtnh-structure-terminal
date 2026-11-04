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
        setTextureName("structureterminal:structure_terminal");
        setCreativeTab(CreativeTabs.tabTools);
        setMaxStackSize(1);
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hx, float hy, float hz) {
        if (NativeBridge.resolve(world, x, y, z, side) == null) return false;
        if (world.isRemote) StructureTerminal.proxy.use(player, x, y, z, side, player.isSneaking());
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote && player.isSneaking()) {
            net.minecraft.util.MovingObjectPosition hit = getMovingObjectPositionFromPlayer(world, player, false);
            if (hit == null || hit.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.MISS)
                StructureTerminal.proxy.settings(player);
        }
        return stack;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(StatCollector.translateToLocal("structureterminal.tooltip"));
        lines.add(StatCollector.translateToLocal("structureterminal.alpha"));
    }
}
