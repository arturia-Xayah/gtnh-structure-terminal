package dev.aestoragevault.world;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dev.aestoragevault.VaultMod;

public class VaultBlock extends BlockContainer {

    public static int renderId;
    private final int kind;
    @SideOnly(Side.CLIENT)
    private IIcon controllerOverlay;
    @SideOnly(Side.CLIENT)
    private IIcon accessOverlay;

    public VaultBlock(int kind) {
        super(Material.iron);
        this.kind = kind;
        setBlockName("aestoragevault." + new String[] { "casing", "controller", "access" }[kind]);
        setHardness(4);
        setResistance(15);
        setStepSound(soundTypeMetal);
        setCreativeTab(CreativeTabs.tabRedstone);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return kind == 1 ? new TileController() : kind == 2 ? new TileAccess() : null;
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return kind != 0;
    }

    @Override
    public int damageDropped(int metadata) {
        return 0;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(net.minecraft.client.renderer.texture.IIconRegister registry) {
        if (kind == 1) controllerOverlay = registry.registerIcon("aestoragevault:controller_overlay");
        if (kind == 2) accessOverlay = registry.registerIcon("aestoragevault:access_overlay");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int metadata) {
        return gregtech.api.GregTechAPI.sBlockCasings1.getIcon(side, 6);
    }

    @SideOnly(Side.CLIENT)
    public IIcon overlay(int side, int metadata) {
        if (kind == 2) return accessOverlay;
        int facing = metadata >= 2 && metadata <= 5 ? metadata : 2;
        return kind == 1 && side == facing ? controllerOverlay : null;
    }

    @Override
    public int getRenderType() {
        return kind == 0 ? 0 : renderId;
    }

    @Override
    public int colorMultiplier(net.minecraft.world.IBlockAccess world, int x, int y, int z) {
        return gregtech.api.GregTechAPI.sBlockCasings1.colorMultiplier(world, x, y, z);
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        if (kind == 1) {
            int[] sides = { 2, 5, 3, 4 };
            int angle = MathHelper.floor_double(placer.rotationYaw * 4 / 360F + 0.5D) & 3;
            world.setBlockMetadataWithNotify(x, y, z, sides[angle], 2);
            if (stack.hasTagCompound() && !world.isRemote)
                ((TileController) world.getTileEntity(x, y, z)).restoreItem(stack.getTagCompound());
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float a, float b,
        float c) {
        if (kind == 0) return false;
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(x, y, z);
            TileController controller = te instanceof TileController ? (TileController) te
                : te instanceof TileAccess ? ((TileAccess) te).bound : null;
            if (controller != null) {
                controller.checkStructure();
                world.markBlockForUpdate(controller.xCoord, controller.yCoord, controller.zCoord);
                player.openGui(VaultMod.INSTANCE, 0, world, controller.xCoord, controller.yCoord, controller.zCoord);
            }
        }
        return true;
    }

    @Override
    public boolean removedByPlayer(World world, net.minecraft.entity.player.EntityPlayer player, int x, int y, int z,
        boolean harvest) {
        // Keep the controller tile alive until its UUID has been written into the harvested item.
        if (kind == 1 && harvest) return true;
        return super.removedByPlayer(world, player, x, y, z, harvest);
    }

    @Override
    public void harvestBlock(World world, net.minecraft.entity.player.EntityPlayer player, int x, int y, int z,
        int metadata) {
        super.harvestBlock(world, player, x, y, z, metadata);
        if (kind == 1) world.setBlockToAir(x, y, z);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<>();
        ItemStack stack = new ItemStack(this);
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileController) stack.setTagCompound(((TileController) te).itemData());
        drops.add(stack);
        return drops;
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        if (!world.isRemote && world.getTileEntity(x, y, z) instanceof TileController)
            ((TileController) world.getTileEntity(x, y, z)).checkStructure();
    }
}
