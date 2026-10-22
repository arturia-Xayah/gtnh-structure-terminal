package dev.aeoptimizer.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.client.me.ItemRepo;

@Mixin(value = ItemRepo.class, remap = false)
public interface ItemRepoAccessor {

    @Accessor("list")
    IItemList<IAEStack<?>> structureterminal$getUnfilteredInventory();
}
