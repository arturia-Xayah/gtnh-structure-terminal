package dev.plasmaforgepower.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.plasmaforgepower.api.ForgePowerAccess;
import dev.plasmaforgepower.core.PowerNbt;
import dev.plasmaforgepower.core.PowerSettings;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTUtil;

@Mixin(value = GTUtil.class, remap = false)
public abstract class DataOrbMixin {

    @Inject(method = "saveMultiblockInputConfiguration", at = @At("RETURN"))
    private static void pfp$saveOrb(MTEMultiBlockBase controller, EntityPlayer player,
        CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || !(controller instanceof ForgePowerAccess)) return;
        ItemStack orb = player.getHeldItem();
        if (orb != null && orb.hasTagCompound()) {
            orb.getTagCompound()
                .setTag(PowerNbt.KEY, PowerNbt.write(((ForgePowerAccess) controller).pfp$getSettings()));
        }
    }

    @Inject(method = "loadMultiblockInputConfiguration", at = @At("RETURN"))
    private static void pfp$loadOrb(MTEMultiBlockBase controller, EntityPlayer player,
        CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || !(controller instanceof ForgePowerAccess)) return;
        ItemStack orb = player.getHeldItem();
        if (orb == null || !orb.hasTagCompound()) return;
        PowerSettings settings = PowerNbt.read(
            orb.getTagCompound()
                .getCompoundTag(PowerNbt.KEY));
        if (settings != null) ((ForgePowerAccess) controller).pfp$applySettings(settings);
    }
}
