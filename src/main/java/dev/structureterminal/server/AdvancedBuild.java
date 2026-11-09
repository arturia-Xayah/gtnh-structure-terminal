package dev.structureterminal.server;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureElement.PlaceResult;

import dev.structureterminal.TerminalConfig;
import dev.structureterminal.TerminalSettings;
import dev.structureterminal.bridge.AdvancedTerminalPlan;
import dev.structureterminal.bridge.NativeBridge;
import dev.structureterminal.bridge.StructureScanner;
import dev.structureterminal.bridge.StructureScanner.Cell;
import gregtech.api.interfaces.IHeatingCoil;

/** One synchronous pass, following the old advanced terminal's skip-and-continue build loop. */
public final class AdvancedBuild {

    private AdvancedBuild() {}

    public static String build(NativeBridge bridge, EntityPlayerMP player, TerminalSettings saved) {
        return build(bridge, player, saved, AeReturn::open);
    }

    static String build(NativeBridge bridge, EntityPlayerMP player, TerminalSettings saved,
        java.util.function.Function<EntityPlayerMP, AeReturn> resolver) {
        if (saved.operation == TerminalSettings.DISMANTLE && !TerminalConfig.demolish)
            throw new IllegalArgumentException("Demolition is disabled by the server");
        boolean aeReturn = saved.network && saved.operation == TerminalSettings.DISMANTLE;
        AeReturn returns = null;
        if (aeReturn || AeReturn.pending(player.getHeldItem())) {
            if (!cpw.mods.fml.common.Loader.isModLoaded("appliedenergistics2"))
                throw new IllegalArgumentException("AE is unavailable; demolition materials remain untouched");
            returns = resolver.apply(player);
            if (returns == null || !returns.flush()) throw new IllegalArgumentException(
                "Cannot return demolition materials to AE. Check link, range, power, storage and insertion permission.");
        }
        if (formed(bridge) && !saved.replacing() && saved.operation == TerminalSettings.BUILD) return "0 / 0 / 0 / 0";
        AdvancedTerminalPlan plan = AdvancedTerminalPlan.create(bridge, player, saved, () -> {
            if (saved.network && !player.capabilities.isCreativeMode)
                for (byte tier = 0; tier <= gregtech.api.enums.HeatingCoilLevel.getMaxTier(); tier++) {
                    ItemStack coil = new ItemStack(
                        gregtech.api.GregTechAPI.sBlockCasings5,
                        1,
                        gregtech.common.blocks.BlockCasings5
                            .getMetaFromCoilHeat(gregtech.api.enums.HeatingCoilLevel.getFromTier(tier)));
                    if (AeSupply.takeOne(player, coil, true)) return tier + 1;
                }
            return 1;
        });
        SelectedSource source = new SelectedSource(player, plan.settings, plan.scan.byPosition, true);
        AutoPlaceEnvironment environment = AutoPlaceEnvironment.fromLegacy(source, player, chat -> {});
        int placed = 0, replaced = 0, removed = 0, blocked = 0;
        long deadline = System.nanoTime() + TerminalConfig.buildMillis * 1000000L;
        try {
            for (Cell cell : plan.scan.cells) {
                if (System.nanoTime() > deadline) throw new IllegalArgumentException(
                    "Build time limit reached; placed " + placed
                        + ", replaced "
                        + replaced
                        + ", removed "
                        + removed
                        + ". Click again to continue.");
                if (cell.x == bridge.tile.xCoord && cell.y == bridge.tile.yCoord && cell.z == bridge.tile.zCoord)
                    continue;
                if (!player.worldObj.blockExists(cell.x, cell.y, cell.z)
                    || !player.worldObj.canMineBlock(player, cell.x, cell.y, cell.z)
                    || !player.canPlayerEdit(cell.x, cell.y, cell.z, 1, player.getHeldItem())) {
                    blocked++;
                    continue;
                }
                StructureScanner.BUILD_CELL.set(cell);
                Block current = player.worldObj.getBlock(cell.x, cell.y, cell.z);
                if (!current.isAir(player.worldObj, cell.x, cell.y, cell.z)) {
                    if (saved.operation == TerminalSettings.DISMANTLE) {
                        if (dismantle(player, saved, cell, current, aeReturn ? returns : null)) removed++;
                        else blocked++;
                        if (AeReturn.pending(player.getHeldItem())) {
                            player.addChatMessage(
                                new net.minecraft.util.ChatComponentTranslation("structureterminal.ae.pending"));
                            break;
                        }
                    } else if (saved.replacing()
                        && (!saved.replaceCoils || cell.heatingCoil && current instanceof IHeatingCoil)) {
                            Replacement.Result result = Replacement
                                .replace(bridge, player, plan.settings, cell, source, cell.advancedTrigger);
                            if (result == Replacement.Result.REPLACED) replaced++;
                            else if (result == Replacement.Result.BLOCKED) blocked++;
                        }
                    continue;
                }
                if (cell.air || saved.replacing() || saved.operation == TerminalSettings.DISMANTLE) continue;
                PlaceResult result = cell.element.survivalPlaceBlock(
                    bridge.context,
                    player.worldObj,
                    cell.x,
                    cell.y,
                    cell.z,
                    cell.advancedTrigger,
                    environment);
                if (result == PlaceResult.ACCEPT || result == PlaceResult.ACCEPT_STOP) placed++;
                else if (result != PlaceResult.SKIP) blocked++;
            }
        } finally {
            StructureScanner.BUILD_CELL.remove();
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
        }
        // Placement sends normal block notifications. Do not force a structure check on running machines.
        return placed + " / " + replaced + " / " + removed + " / " + blocked;
    }

    private static boolean dismantle(EntityPlayerMP player, TerminalSettings settings, Cell cell, Block current,
        AeReturn returns) {
        if (cell.air || cell.candidates.isEmpty()) return false;
        int meta = player.worldObj.getBlockMetadata(cell.x, cell.y, cell.z);
        net.minecraft.tileentity.TileEntity tile = player.worldObj.getTileEntity(cell.x, cell.y, cell.z);
        if (current.getBlockHardness(player.worldObj, cell.x, cell.y, cell.z) < 0) return false;
        ItemStack hatch = tile == null || settings.keepHatches ? null : HatchRecovery.preview(tile, player);
        if ((current.hasTileEntity(meta) || tile != null) && hatch == null) return false;
        java.util.List<ItemStack> recovered = hatch == null
            ? BlockMaterials.recover(player.worldObj, cell.x, cell.y, cell.z, player)
            : java.util.Collections.singletonList(hatch);
        // Only this position's material family qualifies, not every global StructureLib channel indicator.
        boolean known = cell.heatingCoil && current instanceof IHeatingCoil;
        for (ItemStack candidate : cell.candidates) {
            if (Block.getBlockFromItem(candidate.getItem()) == current) known = true;
            for (ItemStack drop : recovered)
                if (dev.structureterminal.bridge.ItemKeys.same(candidate, drop)) known = true;
        }
        if (cell.description.contains("tiered_glass")
            && gregtech.api.util.GlassTier.getGlassBlockTier(current, meta) != null) known = true;
        if (!known || recovered.isEmpty()
            || !(returns == null ? BlockMaterials.canReturn(player, recovered, null) : returns.canAccept(recovered)))
            return false;
        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
            new net.minecraftforge.event.world.BlockEvent.BreakEvent(
                cell.x,
                cell.y,
                cell.z,
                player.worldObj,
                current,
                meta,
                player)))
            return false;
        if (player.worldObj.getBlock(cell.x, cell.y, cell.z) != current
            || player.worldObj.getBlockMetadata(cell.x, cell.y, cell.z) != meta
            || player.worldObj.getTileEntity(cell.x, cell.y, cell.z) != tile) return false;
        if (hatch != null && !ItemStack.areItemStacksEqual(hatch, HatchRecovery.preview(tile, player))) return false;
        if (!(returns == null ? BlockMaterials.canReturn(player, recovered, null) : returns.canAccept(recovered)))
            return false;
        if (returns != null) returns.stage(recovered);
        if (!player.worldObj.setBlockToAir(cell.x, cell.y, cell.z)) {
            if (returns != null) returns.cancel();
            return false;
        }
        if (returns == null) BlockMaterials.give(player, recovered);
        else returns.flush();
        return true;
    }

    private static boolean formed(NativeBridge bridge) {
        if (bridge.context instanceof dev.aestoragevault.world.TileController)
            return ((dev.aestoragevault.world.TileController) bridge.context).formed;
        if (bridge.context instanceof gregtech.api.metatileentity.implementations.MTEMultiBlockBase)
            return ((gregtech.api.metatileentity.implementations.MTEMultiBlockBase) bridge.context).mMachine;
        return false;
    }
}
