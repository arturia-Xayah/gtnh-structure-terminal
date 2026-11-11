package dev.structureterminal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import com.gtnewhorizon.structurelib.ChannelDescription;

import cpw.mods.fml.common.registry.GameRegistry;
import dev.structureterminal.bridge.ItemKeys;
import dev.structureterminal.client.TerminalMaterials;

/** Native projector values, never positions in a sorted list or assumed voltage tiers. */
public final class TerminalChannels {

    public static final String[][] PAGES = {
        { "casing", "machine_casing", "unit_casing", "component_casing", "pipe", "item_pipe", "chassis",
            "coke_oven_casing" },
        { "field_restriction_coil", "solenoid", "capacitor", "cell", "field", "manipulator", "shielding", "antenna" },
        { "motor", "spacetime_compression", "stabilisation", "time_dilation", "piston_block", "construct",
            "essentia_cell", "super_chest" },
        { "length", "width", "boiler", "if_tier", "if_upgrade", "show_errors" } };

    public static boolean toggle(String channel) {
        return "if_upgrade".equals(channel) || "show_errors".equals(channel);
    }

    public static int numericMax(String channel) {
        return "length".equals(channel) ? 99 : "width".equals(channel) ? 3 : 0;
    }

    /** Snapshot after mod registration; duplicate values intentionally retain all native indicator items. */
    public static List<TerminalMaterials.Entry> entries(String channel) {
        List<TerminalMaterials.Entry> result = new ArrayList<>();
        ChannelDescription description = ChannelDescription.getAll()
            .get(channel);
        if (description != null) for (Map.Entry<ItemStack, Integer> e : description.getItems()
            .entrySet()) {
                if (e.getKey() != null && e.getValue() > 0) result.add(
                    new TerminalMaterials.Entry(
                        e.getValue(),
                        e.getValue(),
                        e.getKey()
                            .copy()));
            }
        // These native definitions consume a channel but do not register projector indicator items.
        if (result.isEmpty() && "field".equals(channel)) for (int i = 1; i
            <= kekztech.common.tileentities.MTETankTFFT.Field.VALUES.length; i++)
            result.add(new TerminalMaterials.Entry(i, i, new ItemStack(kekztech.common.Blocks.tfftStorageField, 1, i)));
        if (result.isEmpty() && "essentia_cell".equals(channel)) for (int i = 1; i <= 4; i++) result
            .add(new TerminalMaterials.Entry(i, i, new ItemStack(goodgenerator.loader.Loaders.essentiaCell, 1, i - 1)));
        if (result.isEmpty() && "motor".equals(channel))
            addBlocks(result, gtnhintergalactic.tile.multi.elevator.ElevatorUtil.getMotorTiers());
        if (result.isEmpty() && "piston_block".equals(channel)) addBlocks(
            result,
            gregtech.common.tileentities.machines.multi.MTEElectricImplosionCompressor.getTierBlockList());
        if (result.isEmpty() && "construct".equals(channel)) {
            Block block = GameRegistry.findBlock("Thaumcraft", "blockMetalDevice");
            if (block != null) {
                result.add(new TerminalMaterials.Entry(1, 1, new ItemStack(block, 1, 9)));
                result.add(new TerminalMaterials.Entry(2, 2, new ItemStack(block, 1, 3)));
            }
        }
        if (result.isEmpty() && "super_chest".equals(channel)) {
            gregtech.api.enums.ItemList[] chests = { gregtech.api.enums.ItemList.Super_Chest_LV,
                gregtech.api.enums.ItemList.Super_Chest_MV, gregtech.api.enums.ItemList.Super_Chest_HV,
                gregtech.api.enums.ItemList.Super_Chest_EV, gregtech.api.enums.ItemList.Super_Chest_IV,
                gregtech.api.enums.ItemList.Quantum_Chest_LV, gregtech.api.enums.ItemList.Quantum_Chest_MV,
                gregtech.api.enums.ItemList.Quantum_Chest_HV, gregtech.api.enums.ItemList.Quantum_Chest_EV,
                gregtech.api.enums.ItemList.Quantum_Chest_IV };
            for (int i = 0; i < chests.length; i++)
                result.add(new TerminalMaterials.Entry(i + 1, i + 1, chests[i].get(1)));
        }
        // StructureCompat does not register indicators for Railcraft's pressure selector.
        if ("boiler".equals(channel) && result.isEmpty()) {
            Block boiler = GameRegistry.findBlock("Railcraft", "machine.beta");
            if (boiler != null) {
                result.add(new TerminalMaterials.Entry(1, 1, new ItemStack(boiler, 1, 3)));
                result.add(new TerminalMaterials.Entry(2, 2, new ItemStack(boiler, 1, 4)));
            }
        }
        result.sort(
            Comparator.comparingInt((TerminalMaterials.Entry e) -> e.value)
                .thenComparing(e -> ItemKeys.key(e.icon)));
        result.add(0, new TerminalMaterials.Entry(0, 0, null));
        return result;
    }

    private static void addBlocks(List<TerminalMaterials.Entry> entries,
        List<org.apache.commons.lang3.tuple.Pair<Block, Integer>> blocks) {
        int value = 1;
        for (org.apache.commons.lang3.tuple.Pair<Block, Integer> block : blocks) {
            entries.add(new TerminalMaterials.Entry(value, value, new ItemStack(block.getLeft(), 1, block.getRight())));
            value++;
        }
    }

    private TerminalChannels() {}
}
