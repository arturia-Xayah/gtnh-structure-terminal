package dev.plasmaforgepower.ui;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import gregtech.common.gui.modularui.multiblock.MTEFusionComputerGui;
import gregtech.common.tileentities.machines.multi.MTEFusionComputer;

public final class FusionPowerGui extends MTEFusionComputerGui {

    public FusionPowerGui(MTEFusionComputer fusion) {
        super(fusion);
    }

    @Override
    public ModularPanel build(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        return new PowerPanel(multiblock).attach(super.build(data, sync, settings), sync);
    }
}
