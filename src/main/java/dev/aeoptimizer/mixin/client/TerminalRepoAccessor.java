package dev.aeoptimizer.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.me.ItemRepo;

@Mixin(value = GuiMEMonitorable.class, remap = false)
public interface TerminalRepoAccessor {

    @Accessor("repo")
    ItemRepo structureterminal$getRepo();
}
