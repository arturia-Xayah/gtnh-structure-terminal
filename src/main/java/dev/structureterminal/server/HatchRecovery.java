package dev.structureterminal.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidTankInfo;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;

/** Native GT hatch item NBT, without calling getDrops() during a dry run (that invokes destruction hooks). */
final class HatchRecovery {

    static ItemStack preview(TileEntity tile, EntityPlayerMP player) {
        if (!(tile instanceof BaseMetaTileEntity)) return null;
        BaseMetaTileEntity base = (BaseMetaTileEntity) tile;
        if (!(base.getMetaTileEntity() instanceof MTEHatch)) return null;
        MTEHatch hatch = (MTEHatch) base.getMetaTileEntity();
        // Unknown subclasses may keep inventories outside IInventory/IFluidHandler.
        // Only opt in native basic hatches whose storage is covered by the checks below.
        String kind = hatch.getClass()
            .getName();
        if (!kind.startsWith("gregtech.api.metatileentity.implementations.")) return null;
        String simple = hatch.getClass()
            .getSimpleName();
        if (!java.util.Arrays
            .asList(
                "MTEHatchInputBus",
                "MTEHatchOutputBus",
                "MTEHatchInput",
                "MTEHatchMultiInput",
                "MTEHatchOutput",
                "MTEHatchEnergy",
                "MTEHatchDynamo",
                "MTEHatchMaintenance",
                "MTEHatchMuffler")
            .contains(simple)) return null;
        if (!hatch.isAccessAllowed(player) || base.privateAccess() && !player.getUniqueID()
            .equals(base.getOwnerUuid())) return null;
        // Non-empty/special stores retain their native lifecycle rather than losing contents or creating loose drops.
        for (int i = 0; i < base.getSizeInventory(); i++) if (base.getStackInSlot(i) != null) return null;
        for (ForgeDirection side : ForgeDirection.values()) {
            FluidTankInfo[] tanks = base.getTankInfo(side);
            if (tanks != null) for (FluidTankInfo tank : tanks)
                if (tank != null && tank.fluid != null && tank.fluid.amount > 0) return null;
        }
        if (hatch.getDroppedItem() != null) return null;
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
