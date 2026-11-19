package dev.structureterminal.bc.mixin;

import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;

import buildcraft.builders.BlockArchitect;
import buildcraft.builders.BlockFiller;
import buildcraft.core.lib.block.BlockBuildCraft;
import dev.structureterminal.bc.PortableRange;
import dev.structureterminal.bc.RangeMachine;

@Mixin(value = { BlockFiller.class, BlockArchitect.class }, remap = false)
public abstract class RangeBlockMixin extends BlockBuildCraft {

    protected RangeBlockMixin(Material material) {
        super(material);
    }

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        return willHarvest || super.removedByPlayer(world, player, x, y, z, false);
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, int x, int y, int z, int meta) {
        try {
            super.harvestBlock(world, player, x, y, z, meta);
        } finally {
            if (world.getBlock(x, y, z) == (Object) this) world.setBlockToAir(x, y, z);
        }
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        TileEntity tile = world.getTileEntity(x, y, z);
        if (world.isRemote || !(tile instanceof RangeMachine) || !stack.hasTagCompound()) return;
        int[] size = stack.getTagCompound()
            .getIntArray(PortableRange.KEY);
        if (size.length != 3) return;
        try {
            PortableRange.apply(tile, size);
        } catch (IllegalArgumentException invalid) {
            // Keep the settings available for mining and placing again at a valid height.
            ((RangeMachine) tile).rangeData()
                .setIntArray(PortableRange.KEY, size.clone());
            tile.markDirty();
            if (placer instanceof EntityPlayer)
                ((EntityPlayer) placer).addChatMessage(new ChatComponentTranslation(invalid.getMessage()));
        }
    }
}
