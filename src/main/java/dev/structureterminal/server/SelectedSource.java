package dev.structureterminal.server;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.gtnewhorizon.structurelib.structure.IItemSource;

import cpw.mods.fml.common.Loader;
import dev.structureterminal.TerminalSettings;
import dev.structureterminal.bridge.ItemKeys;
import dev.structureterminal.bridge.StructureScanner;
import dev.structureterminal.bridge.StructureScanner.Cell;

/** Native placement retains all hatch checks; this source only restricts which material may be taken. */
public final class SelectedSource implements IItemSource {

    private final IItemSource source;
    private final TerminalSettings settings;
    private final EntityPlayerMP player;
    private final Map<String, Cell> cells;

    public SelectedSource(EntityPlayerMP player, TerminalSettings settings, Map<String, Cell> cells) {
        this.player = player;
        this.settings = settings;
        this.cells = cells;
        source = IItemSource.fromInventory(player.inventory);
    }

    @Override
    public boolean takeOne(ItemStack stack, boolean simulate) {
        if (stack == null || stack.stackSize != 1) throw new IllegalArgumentException();
        Cell cell = StructureScanner.BUILD_CELL.get();
        if (cell == null || cells.get(cell.position()) != cell
            || !player.worldObj.blockExists(cell.x, cell.y, cell.z)
            || !player.worldObj.canMineBlock(player, cell.x, cell.y, cell.z)
            || !player.canPlayerEdit(cell.x, cell.y, cell.z, 1, player.getHeldItem())) return false;
        ItemStack selected = cell.selected(settings);
        if (selected != null && !ItemKeys.same(selected, stack)) return false;
        if (player.capabilities.isCreativeMode) return true;
        if (source.takeOne(stack, simulate)) return true;
        return settings.network && Loader.isModLoaded("appliedenergistics2")
            && AeSupply.takeOne(player, stack, simulate);
    }

    @Override
    public Map<ItemStack, Integer> take(Predicate<ItemStack> predicate, boolean simulate, int count) {
        Cell cell = StructureScanner.BUILD_CELL.get();
        if (cell == null || cells.get(cell.position()) != cell || count < 1) return Collections.emptyMap();
        if (!player.worldObj.blockExists(cell.x, cell.y, cell.z)
            || !player.worldObj.canMineBlock(player, cell.x, cell.y, cell.z)
            || !player.canPlayerEdit(cell.x, cell.y, cell.z, 1, player.getHeldItem())) return Collections.emptyMap();
        ItemStack chosen = cell.selected(settings);
        Predicate<ItemStack> allowed = chosen == null ? predicate : s -> ItemKeys.same(s, chosen) && predicate.test(s);
        if (player.capabilities.isCreativeMode) {
            for (ItemStack candidate : cell.candidates) if (allowed.test(candidate)) {
                ItemStack copy = candidate.copy();
                copy.stackSize = count;
                Map<ItemStack, Integer> result = new LinkedHashMap<ItemStack, Integer>();
                result.put(copy, count);
                return result;
            }
        }
        Map<ItemStack, Integer> carried = source.take(allowed, simulate, count);
        int have = 0;
        for (int n : carried.values()) have += n;
        if (!settings.network || have >= count || !Loader.isModLoaded("appliedenergistics2")) return carried;
        Map<ItemStack, Integer> result = new LinkedHashMap<ItemStack, Integer>(carried);
        result.putAll(AeSupply.take(player, allowed, simulate, count - have));
        return result;
    }
}
