package dev.structureterminal.bridge;

import java.util.Collections;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureElement;

import dev.structureterminal.TerminalSettings;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.interfaces.IHeatingCoil;
import gregtech.api.metatileentity.implementations.MTEHatch;

/** GTNH adapter for the five settings of GTMThings 1.5.4-GTO-Port's advanced terminal. */
public final class AdvancedTerminalPlan {

    public final TerminalSettings settings;
    public final StructureScanner.Scan scan;

    private AdvancedTerminalPlan(TerminalSettings settings, StructureScanner.Scan scan) {
        this.settings = settings;
        this.scan = scan;
    }

    public static AdvancedTerminalPlan create(NativeBridge bridge, EntityPlayer player, TerminalSettings saved) {
        return create(bridge, player, saved, () -> 1);
    }

    public static AdvancedTerminalPlan create(NativeBridge bridge, EntityPlayer player, TerminalSettings saved,
        java.util.function.IntSupplier externalCoil) {
        TerminalSettings settings = TerminalSettings.read(saved.write());
        settings.size = Math.max(1, settings.repeatCount);
        settings.mirror = bridge.alignment != null && bridge.alignment.getFlip()
            .isHorizontallyFlipped();
        settings.channels.clear();
        settings.choices.clear();
        // Preserve the actual native glass channel ID, not its position in the sorted UI list.
        Integer glass = saved.channels.get("glass");
        if (glass != null && glass > 0
            && glass <= gregtech.api.util.GlassTier.getGlassList()
                .size())
            settings.channels.put("glass", glass);
        if (settings.glassTier >= 3) {
            java.util.List<dev.structureterminal.client.TerminalMaterials.Entry> options = dev.structureterminal.client.TerminalMaterials
                .variants(settings.glassTier);
            if (options.size() > 1 && (glass == null || options.stream()
                .noneMatch(e -> e.value == glass))) settings.channels.put("glass", options.get(1).value);
        }
        // Enumerate and permit native hatch placement. The material source filters optional hatches separately.
        settings.channels.put("gt_hatch", 1);
        settings.channels.put("coil", Math.max(1, settings.coilTier));
        StructureScanner.Scan scan = StructureScanner.scan(bridge, player, settings);
        if (settings.glassTier >= 3 && (glass == null || glass == 0)) {
            int chosenGlass = 0;
            for (StructureScanner.Cell cell : scan.cells) {
                boolean glassCell = cell.description.contains("glass");
                for (ItemStack candidate : cell.candidates) if (gregtech.api.util.GlassTier
                    .getGlassBlockTier(Block.getBlockFromItem(candidate.getItem()), candidate.getItemDamage()) != null)
                    glassCell = true;
                if (!glassCell) continue;
                Block existing = player.worldObj.getBlock(cell.x, cell.y, cell.z);
                int meta = player.worldObj.getBlockMetadata(cell.x, cell.y, cell.z);
                if (Integer.valueOf(settings.glassTier)
                    .equals(gregtech.api.util.GlassTier.getGlassBlockTier(existing, meta))) {
                    chosenGlass = gregtech.api.util.GlassTier.getGlassChannelValue(existing, meta);
                    break;
                }
            }
            if (chosenGlass == 0) for (ItemStack stack : player.inventory.mainInventory) {
                if (stack != null && Integer.valueOf(settings.glassTier)
                    .equals(
                        gregtech.api.util.GlassTier
                            .getGlassBlockTier(Block.getBlockFromItem(stack.getItem()), stack.getItemDamage()))) {
                    chosenGlass = gregtech.api.util.GlassTier
                        .getGlassChannelValue(Block.getBlockFromItem(stack.getItem()), stack.getItemDamage());
                    break;
                }
            }
            if (chosenGlass == 0 && settings.network
                && !player.worldObj.isRemote
                && player instanceof net.minecraft.entity.player.EntityPlayerMP) {
                for (dev.structureterminal.client.TerminalMaterials.Entry e : dev.structureterminal.client.TerminalMaterials
                    .variants(settings.glassTier))
                    if (e.value > 0 && dev.structureterminal.server.AeSupply
                        .takeOne((net.minecraft.entity.player.EntityPlayerMP) player, e.icon, true)) {
                            chosenGlass = e.value;
                            break;
                        }
            }
            if (chosenGlass > 0 && !Integer.valueOf(chosenGlass)
                .equals(settings.channels.get("glass"))) {
                settings.channels.put("glass", chosenGlass);
                scan = StructureScanner.scan(bridge, player, settings);
            }
        }
        int coil = settings.coilTier;
        if (coil == 0) {
            // Preserve an already partially built coil ring before selecting material from the inventory.
            for (StructureScanner.Cell cell : scan.cells) if (isCoilCell(cell)) {
                int tier = coilTier(
                    player.worldObj.getBlock(cell.x, cell.y, cell.z),
                    player.worldObj.getBlockMetadata(cell.x, cell.y, cell.z));
                if (tier > 0) {
                    coil = tier;
                    break;
                }
            }
            if (coil == 0) for (ItemStack stack : player.inventory.mainInventory) {
                int tier = coilTier(stack);
                if (tier > 0 && (coil == 0 || tier < coil)) coil = tier;
            }
            if (coil == 0) coil = Math.max(1, externalCoil.getAsInt());
        }
        for (StructureScanner.Cell cell : scan.cells) {
            cell.heatingCoil = isCoilCell(cell);
            cell.advancedTrigger = settings.trigger();
            if (!cell.heatingCoil) continue;
            // Bare GT coil elements use stack size; channel-wrapped ones use the coil subchannel.
            cell.advancedTrigger.stackSize = coil;
            com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor
                .setChannelData(cell.advancedTrigger, "coil", coil);
            IStructureElement.BlocksToPlace blocks = cell.element.getBlocksToPlace(
                bridge.context,
                player.worldObj,
                cell.x,
                cell.y,
                cell.z,
                cell.advancedTrigger,
                AutoPlaceEnvironment.fromLegacy((p, simulate, count) -> Collections.emptyMap(), player, chat -> {}));
            CandidateDiscovery.Result candidates = CandidateDiscovery
                .resolve(cell.element, blocks, GregTechCatalog.items(), System.nanoTime() + 100000000L);
            cell.candidates = candidates.items;
            cell.keyedCandidates = new java.util.LinkedHashMap<String, ItemStack>();
            for (ItemStack candidate : cell.candidates) cell.keyedCandidates.put(ItemKeys.key(candidate), candidate);
        }
        return new AdvancedTerminalPlan(settings, scan);
    }

    private static boolean isCoilCell(StructureScanner.Cell cell) {
        for (ItemStack stack : cell.candidates) if (coilTier(stack) > 0) return true;
        return cell.description.contains("GT5U.structure.heating_coil");
    }

    public static int coilTier(ItemStack stack) {
        return stack == null ? 0 : coilTier(Block.getBlockFromItem(stack.getItem()), stack.getItemDamage());
    }

    private static int coilTier(Block block, int meta) {
        if (!(block instanceof IHeatingCoil)) return 0;
        HeatingCoilLevel heat = ((IHeatingCoil) block).getCoilHeat(meta);
        return heat == null ? 0 : Math.max(0, heat.getTier() + 1);
    }

    public static boolean isHatch(ItemStack stack) {
        if (stack == null || Block.getBlockFromItem(stack.getItem()) != GregTechAPI.sBlockMachines) return false;
        int meta = stack.getItemDamage();
        return meta >= 0 && meta < GregTechAPI.METATILEENTITIES.length
            && GregTechAPI.METATILEENTITIES[meta] instanceof MTEHatch;
    }

    public static boolean allowMaterial(TerminalSettings settings, StructureScanner.Cell cell, ItemStack stack) {
        if (!settings.noHatches || cell == null || !isHatch(stack)) return true;
        // Like the old GTO tooltip: suppress optional hatches, retain exclusive hatch-only positions.
        for (ItemStack candidate : cell.candidates) if (!isHatch(candidate)) return false;
        return true;
    }
}
