package dev.aestoragevault.mixin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.aestoragevault.world.ReturnBus;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;

@Mixin(value = MTEHatchOutputBus.class, remap = false)
public abstract class ReturnBusMixin implements ReturnBus {

    @Unique
    private UUID vault$busId;
    @Unique
    private final Map<UUID, UUID> vault$deliveries = new HashMap<>();

    @Override
    public UUID vaultBusId() {
        if (vault$busId == null) vault$busId = UUID.randomUUID();
        return vault$busId;
    }

    @Override
    public Map<UUID, UUID> vaultDeliveries() {
        return vault$deliveries;
    }

    @Inject(method = "saveNBTData", at = @At("TAIL"))
    private void vault$save(NBTTagCompound tag, CallbackInfo ci) {
        if (vault$busId == null && vault$deliveries.isEmpty()) return;
        tag.setString("VaultReturnBusId", vaultBusId().toString());
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, UUID> e : vault$deliveries.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString(
                "Token",
                e.getKey()
                    .toString());
            t.setString(
                "Vault",
                e.getValue()
                    .toString());
            list.appendTag(t);
        }
        tag.setTag("VaultReturnDeliveries", list);
    }

    @Inject(method = "loadNBTData", at = @At("TAIL"))
    private void vault$load(NBTTagCompound tag, CallbackInfo ci) {
        vault$busId = tag.hasKey("VaultReturnBusId") ? UUID.fromString(tag.getString("VaultReturnBusId")) : null;
        vault$deliveries.clear();
        NBTTagList list = tag.getTagList("VaultReturnDeliveries", 10);
        if (list.tagCount() > 16) throw new IllegalArgumentException("Too many pending return deliveries");
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            vault$deliveries.put(UUID.fromString(t.getString("Token")), UUID.fromString(t.getString("Vault")));
        }
    }

    @Inject(method = "onPostTick", at = @At("HEAD"), cancellable = true)
    private void vault$hold(IGregTechTileEntity base, long tick, CallbackInfo ci) {
        if (!vault$deliveries.isEmpty()) ci.cancel();
    }

    @Inject(method = "allowPullStack", at = @At("HEAD"), cancellable = true)
    private void vault$pull(IGregTechTileEntity base, int slot, ForgeDirection side, ItemStack stack,
        CallbackInfoReturnable<Boolean> cir) {
        if (!vault$deliveries.isEmpty()) cir.setReturnValue(false);
    }

    @Inject(method = "onRightclick", at = @At("HEAD"), cancellable = true)
    private void vault$gui(IGregTechTileEntity base, EntityPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (!vault$deliveries.isEmpty()) cir.setReturnValue(true);
    }
}
