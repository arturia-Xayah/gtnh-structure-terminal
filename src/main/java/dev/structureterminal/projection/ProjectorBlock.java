package dev.structureterminal.projection;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dev.structureterminal.StructureTerminal;

public final class ProjectorBlock extends BlockContainer {

    private IIcon emitter;

    private ProjectorBlock() {
        super(Material.iron);
        setHardness(4);
        setResistance(8);
        setStepSound(soundTypeMetal);
        setBlockName("structureterminal.projector");
        setBlockTextureName("structureterminal:projector_side");
        setCreativeTab(CreativeTabs.tabRedstone);
    }

    public static void register() {
        GameRegistry.registerBlock(new ProjectorBlock(), "portal_projector");
        GameRegistry.registerTileEntity(ProjectorTile.class, "structureterminal.portal_projector");
    }

    @Override
    public TileEntity createNewTileEntity(World w, int meta) {
        return new ProjectorTile();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister atlas) {
        super.registerBlockIcons(atlas);
        emitter = atlas.registerIcon("structureterminal:projector_top");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return side == 1 ? emitter : blockIcon;
    }

    @Override
    public void onBlockPlacedBy(World w, int x, int y, int z, EntityLivingBase e, ItemStack stack) {
        if (w.isRemote) return;
        int turn = MathHelper.floor_double(e.rotationYaw / 90 + .5) & 3;
        w.setBlockMetadataWithNotify(x, y, z, turn, 3);

    }

    @Override
    public void breakBlock(World w, int x, int y, int z, net.minecraft.block.Block block, int metadata) {
        TileEntity t = w.getTileEntity(x, y, z);
        if (!w.isRemote && t instanceof ProjectorTile) GateDirectory.get()
            .remove((ProjectorTile) t);
        super.breakBlock(w, x, y, z, block, metadata);
    }

    @Override
    public boolean rotateBlock(World w, int x, int y, int z, net.minecraftforge.common.util.ForgeDirection axis) {
        return false;
    }

    @Override
    public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer p, int side, float hx, float hy,
        float hz) {
        if (!w.isRemote) p.openGui(StructureTerminal.instance, ProjectorMenu.ID, w, x, y, z);
        return true;
    }
}
