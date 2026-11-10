package dev.structureterminal.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;

/** Native GT hatch item NBT, without calling getDrops() during a dry run (that invokes destruction hooks). */
final class HatchRecovery {

    static ItemStack preview(TileEntity tile, EntityPlayerMP player) {
        if (tile instanceof appeng.api.networking.IGridHost) {
            for (net.minecraftforge.common.util.ForgeDirection side : net.minecraftforge.common.util.ForgeDirection
                .values()) {
                appeng.api.networking.IGridNode node = ((appeng.api.networking.IGridHost) tile).getGridNode(side);
                if (node == null) continue;
                appeng.api.networking.security.ISecurityGrid security = node.getGrid()
                    .getCache(appeng.api.networking.security.ISecurityGrid.class);
                if (security != null && !security.hasPermission(player, appeng.api.config.SecurityPermissions.BUILD))
                    return null;
            }
        }
        if (tile instanceof dev.aestoragevault.world.TileAccess) {
            dev.aestoragevault.world.TileAccess port = (dev.aestoragevault.world.TileAccess) tile;
            java.util.List<ItemStack> drops = port.getBlockType()
                .getDrops(port.getWorldObj(), port.xCoord, port.yCoord, port.zCoord, port.getBlockMetadata(), 0);
            return drops.size() == 1 ? drops.get(0) : null;
        }
        if (!(tile instanceof BaseMetaTileEntity)) return null;
        BaseMetaTileEntity base = (BaseMetaTileEntity) tile;
        if (!(base.getMetaTileEntity() instanceof MTEHatch)) return null;
        MTEHatch hatch = (MTEHatch) base.getMetaTileEntity();
        if (!hatch.isAccessAllowed(player) || base.privateAccess() && !player.getUniqueID()
            .equals(base.getOwnerUuid())) return null;
        NBTTagCompound saved = new NBTTagCompound();
        base.writeToNBT(saved);
        NBTTagCompound item = new NBTTagCompound();
        if (saved.getBoolean("mLockUpgrade")) item.setBoolean("mLockUpgrade", true);
        if (base.getColorization() >= 0) item.setByte("mColor", (byte) (base.getColorization() + 1));
        if (saved.getByte("mOtherUpgrades") > 0) item.setByte("mOtherUpgrades", saved.getByte("mOtherUpgrades"));
        if (saved.hasKey(GTValues.NBT.COVERS)) item.setTag(
            GTValues.NBT.COVERS,
            saved.getTag(GTValues.NBT.COVERS)
                .copy());
        if (saved.getByte("mStrongRedstone") > 0) item.setByte("mStrongRedstone", saved.getByte("mStrongRedstone"));
        hatch.setItemNBT(item);
        ItemStack drop = new ItemStack(GregTechAPI.sBlockMachines, 1, base.getMetaTileID());
        if (!item.hasNoTags()) drop.setTagCompound(item);
        return drop;
    }

    private HatchRecovery() {}
}
