package dev.structureterminal.bc.mixin;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.recursive_pineapple.matter_manipulator.client.rendering.BoxRenderer;
import com.recursive_pineapple.matter_manipulator.common.building.PendingBlock;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMRenderer;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState;

import dev.structureterminal.bc.MachineRangeCopy;
import dev.structureterminal.bc.PlannedRange;

@Mixin(value = MMRenderer.class, remap = false)
public abstract class PastePreviewMixin {

    @Shadow
    private static List<PendingBlock> analysisCache;
    @Unique
    private static java.lang.ref.WeakReference<List<PendingBlock>> range$last = new java.lang.ref.WeakReference<>(null);
    @Unique
    private static final List<AxisAlignedBB> range$boxes = new ArrayList<>();
    @Unique
    private static final Vector3f range$colour = new Vector3f(.32f, .85f, 1f);

    @Inject(
        method = "renderRegions",
        at = @At(
            value = "INVOKE",
            target = "Lcom/recursive_pineapple/matter_manipulator/common/items/manipulator/MMRenderer;showHudText(Ljava/lang/String;)V",
            ordinal = 0))
    private static void range$draw(RenderWorldLastEvent event, EntityPlayer player, MMState state,
        ItemMatterManipulator mm, CallbackInfo ci) {
        if (state.config.placeMode != MMState.PlaceMode.COPYING || analysisCache == null) return;
        if (range$last.get() != analysisCache) {
            range$last = new java.lang.ref.WeakReference<>(analysisCache);
            range$boxes.clear();
            for (PendingBlock block : analysisCache) {
                MachineRangeCopy copy = ((PlannedRange) block).machineRange();
                if (copy == null) continue;
                try {
                    int[] c = copy.bounds(player.worldObj, block.x, block.y, block.z);
                    range$boxes.add(AxisAlignedBB.getBoundingBox(c[0], c[1], c[2], c[3] + 1, c[4] + 1, c[5] + 1));
                } catch (IllegalArgumentException outsideWorld) { /* This selection cannot be applied here. */ }
            }
        }
        for (AxisAlignedBB box : range$boxes) BoxRenderer.INSTANCE.drawAround(box, range$colour);
    }
}
