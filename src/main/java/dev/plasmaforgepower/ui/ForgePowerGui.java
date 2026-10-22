package dev.plasmaforgepower.ui;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import gregtech.common.gui.modularui.multiblock.MTEPlasmaForgeGui;
import gregtech.common.tileentities.machines.multi.MTEPlasmaForge;

public final class ForgePowerGui extends MTEPlasmaForgeGui {

    public ForgePowerGui(MTEPlasmaForge forge) {
        super(forge);
    }

    @Override
    public ModularPanel build(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        return new PowerPanel(multiblock).attach(super.build(data, sync, settings), sync);
    }

    public ModularPanel createPowerPanel(PanelSyncManager sync) {
        return new PowerPanel(multiblock).createPowerPanel(sync);
    }
}
