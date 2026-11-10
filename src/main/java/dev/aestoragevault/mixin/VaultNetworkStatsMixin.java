package dev.aestoragevault.mixin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizon.gtnhlib.util.map.ItemStackMap;

import appeng.api.storage.ICellProvider;
import appeng.me.cache.GridStorageCache;
import dev.aestoragevault.VaultMod;
import dev.aestoragevault.core.VaultStore;
import dev.aestoragevault.world.TileAccess;
import dev.aestoragevault.world.TileOutput;

/** Native network-tool statistics enumerate drive/chest providers only; append our shared two-channel store. */
@Mixin(value = GridStorageCache.class, remap = false)
public abstract class VaultNetworkStatsMixin {

    @Shadow
    @Final
    private HashSet<ICellProvider> activeCellProviders;
    @Shadow
    @Final
    private ItemStackMap<Integer> itemCells;
    @Shadow
    @Final
    private ItemStackMap<Integer> fluidCells;

    @Shadow
    private void updateItemCellStatus(double total, double used, int status, double types, double usedTypes) {
        throw new AssertionError();
    }

    @Shadow
    private void updateFluidCellStatus(double total, double used, int status, double types, double usedTypes) {
        throw new AssertionError();
    }

    @Shadow
    private void putItemStackIntoMap(ItemStackMap<Integer> map, ItemStack item, int status) {
        throw new AssertionError();
    }

    @Inject(method = "updateBytesInfo", at = @At("TAIL"))
    private void vault$stats(CallbackInfo ci) {
        Set<UUID> seen = new HashSet<>();
        for (ICellProvider provider : activeCellProviders) {
            if (!(provider instanceof TileAccess) || provider instanceof TileOutput) continue;
            TileAccess access = (TileAccess) provider;
            if (!access.available() || !seen.add(access.bound.id)) continue;
            VaultStore store = access.bound.store();
            if (store == null) continue;
            int status = store.usedBytesCeil()
                .compareTo(store.capacity()) >= 0 ? 4 : 1;
            updateItemCellStatus(
                number(store.channelCapacity("items")),
                number(store.channelUsedBytes("items")),
                status,
                Double.POSITIVE_INFINITY,
                store.channelTypes("items"));
            updateFluidCellStatus(
                number(store.channelCapacity("fluids")),
                number(store.channelUsedBytes("fluids")),
                status,
                Double.POSITIVE_INFINITY,
                store.channelTypes("fluids"));
            putItemStackIntoMap(itemCells, new ItemStack(VaultMod.CONTROLLER), status);
            putItemStackIntoMap(fluidCells, new ItemStack(VaultMod.CONTROLLER), status);
        }
    }

    private static double number(java.math.BigInteger n) {
        return Math.min(Double.MAX_VALUE, n.doubleValue());
    }
}
