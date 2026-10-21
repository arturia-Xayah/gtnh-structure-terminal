package dev.aeoptimizer.network;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import dev.aeoptimizer.OrderOptimizer;

public class BlockOptimizer extends BlockContainer {

    public BlockOptimizer() {
        super(Material.iron);
        setBlockName("aeorderoptimizer.core");
        setBlockTextureName("aeorderoptimizer:core");
        setHardness(3);
        setResistance(10);
        setCreativeTab(CreativeTabs.tabRedstone);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileOptimizer();
    }

    @Override
    public boolean canPlaceBlockAt(World world, int x, int y, int z) {
        return super.canPlaceBlockAt(world, x, y, z) && !OptimizerNetworks.hasNeighborOptimizer(world, x, y, z, null);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float a, float b,
        float c) {
        if (!world.isRemote) player.openGui(OrderOptimizer.INSTANCE, 0, world, x, y, z);
        return true;
    }
}
