package dev.plasmaforgepower.ui;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import goodgenerator.blocks.tileEntity.base.MTELargeFusionComputer;
import gregtech.common.gui.modularui.multiblock.base.TTMultiblockBaseGui;

public final class CompactFusionPowerGui extends TTMultiblockBaseGui<MTELargeFusionComputer> {

    public CompactFusionPowerGui(MTELargeFusionComputer fusion) {
        super(fusion);
    }

    @Override
    public ModularPanel build(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        return new PowerPanel(multiblock).attach(super.build(data, sync, settings), sync);
    }
}
