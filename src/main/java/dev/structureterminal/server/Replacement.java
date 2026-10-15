package dev.structureterminal.server;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;

import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureElement.PlaceResult;

import dev.structureterminal.TerminalSettings;
import dev.structureterminal.bridge.NativeBridge;
import dev.structureterminal.bridge.StructureScanner.Cell;

/** Confirmed replacement of ordinary blocks; material is charged only after successful native placement. */
final class Replacement {

    enum Result {
        SKIPPED,
        REPLACED,
        BLOCKED
    }

    private Replacement() {}

    static Result replace(NativeBridge bridge, EntityPlayerMP player, TerminalSettings settings, Cell cell,
        SelectedSource source) {
        if (cell.air || cell.candidates.isEmpty()) return Result.SKIPPED;
        ItemStack selected = cell.selected(settings);
        final ItemStack target = (selected == null ? cell.candidates.get(0) : selected).copy();
        target.stackSize = 1;
        if (!(target.getItem() instanceof ItemBlock)) return Result.SKIPPED;
        Block wanted = Block.getBlockFromItem(target.getItem());
        int wantedMeta = ((ItemBlock) target.getItem()).getMetadata(target.getItemDamage());
        Block old = player.worldObj.getBlock(cell.x, cell.y, cell.z);
        int meta = player.worldObj.getBlockMetadata(cell.x, cell.y, cell.z);
        if (old.isAir(player.worldObj, cell.x, cell.y, cell.z) || (old == wanted && meta == wantedMeta))
            return Result.SKIPPED;
        if (wanted.hasTileEntity(wantedMeta) || old.hasTileEntity(meta)
            || player.worldObj.getTileEntity(cell.x, cell.y, cell.z) != null
            || old.getBlockHardness(player.worldObj, cell.x, cell.y, cell.z) < 0) return Result.SKIPPED;
        List<ItemStack> recovered = BlockMaterials.recover(player.worldObj, cell.x, cell.y, cell.z, player);
        if (!BlockMaterials.known(cell, old, recovered)) return Result.SKIPPED;
        if (!source.takeOne(target, true)) return Result.BLOCKED;
        if (MinecraftForge.EVENT_BUS
            .post(new BlockEvent.BreakEvent(cell.x, cell.y, cell.z, player.worldObj, old, meta, player)))
            return Result.SKIPPED;
        // The virtual source limits native placement to one authorized item; failed placements never consume stock.
        final int[] used = { 0 };
        IItemSource deferred = new IItemSource() {

            @Override
            public Map<ItemStack, Integer> take(Predicate<ItemStack> predicate, boolean simulate, int count) {
                if (count < 1 || used[0] != 0 || !predicate.test(target)) return Collections.emptyMap();
                if (!simulate) used[0] = 1;
                return Collections.singletonMap(target.copy(), 1);
            }
        };
        boolean committed = false;
        try {
            if (!player.worldObj.setBlockToAir(cell.x, cell.y, cell.z)) return Result.SKIPPED;
            PlaceResult result = cell.element.survivalPlaceBlock(
                bridge.context,
                player.worldObj,
                cell.x,
                cell.y,
                cell.z,
                settings.trigger(),
                AutoPlaceEnvironment.fromLegacy(deferred, player, chat -> {}));
            if (result != PlaceResult.ACCEPT && result != PlaceResult.ACCEPT_STOP) return Result.BLOCKED;
            if (used[0] != 1 || player.worldObj.getBlock(cell.x, cell.y, cell.z) != wanted
                || player.worldObj.getBlockMetadata(cell.x, cell.y, cell.z) != wantedMeta) return Result.BLOCKED;
            if (!source.takeOne(target, false)) return Result.BLOCKED;
            committed = true;
            BlockMaterials.give(player, recovered);
            return Result.REPLACED;
        } finally {
            if (!committed) player.worldObj.setBlock(cell.x, cell.y, cell.z, old, meta, 3);
        }
    }
}
