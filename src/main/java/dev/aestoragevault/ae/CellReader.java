package dev.aestoragevault.ae;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.definitions.IItemDefinition;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEItemStack;
import dev.aestoragevault.core.Resource;

/** Reads a COPY. No extraction from or mutation of the original cell is needed before durable commit. */
public final class CellReader {

    public final long bytes;
    public final Map<Resource, Long> contents = new HashMap<>();

    private CellReader(long bytes) {
        this.bytes = bytes;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static CellReader read(ItemStack original) throws IOException {
        if (original == null || original.stackSize < 1) throw new IOException("Empty slot");
        ItemStack copy = original.copy();
        long part = componentBytes(copy);
        if (part > 0) return new CellReader(Math.multiplyExact(part, copy.stackSize));
        if (!(copy.getItem() instanceof IStorageCell)) throw new IOException("Not a supported storage cell/component");
        IStorageCell cell = (IStorageCell) copy.getItem();
        if (!cell.isStorageCell(copy)) throw new IOException("This item is not an active storage cell");
        if (copy.stackSize != 1) throw new IOException("Filled cells must be unstacked");
        long bytes = cell.getBytesLong(copy);
        if (bytes <= 0) throw new IOException("Creative/void/zero-capacity cells cannot be absorbed");
        IAEStackType type = cell.getStackType();
        if (type != appeng.util.item.AEItemStackType.ITEM_STACK_TYPE
            && type != appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE)
            throw new IOException("Only item/fluid cells are supported; source retained");
        IMEInventoryHandler handler = AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(copy, null, type);
        if (!(handler instanceof ICellInventoryHandler))
            throw new IOException("Cell contents cannot be inspected safely");
        ICellInventory inv = ((ICellInventoryHandler) handler).getCellInv();
        if (inv == null || inv.getTotalBytes() != bytes) throw new IOException("Cell capacity mismatch");
        CellReader result = new CellReader(bytes);
        IItemList list = type.createPrimitiveList();
        handler.getAvailableItems(list);
        for (Object o : list) {
            IAEStack stack = (IAEStack) o;
            if (stack.getStackSize() > 0) result.add(ResourceCodec.encode(stack), stack.getStackSize());
        }
        // Cards are real items too; migrate them instead of silently deleting them with the housing.
        IInventory cards = inv.getUpgradesInventory();
        if (cards != null) for (int i = 0; i < cards.getSizeInventory(); i++) {
            ItemStack card = cards.getStackInSlot(i);
            if (card != null) result.add(ResourceCodec.encode(AEItemStack.create(card)), card.stackSize);
        }
        return result;
    }

    private void add(Resource r, long count) {
        contents.put(r, Math.addExact(contents.getOrDefault(r, 0L), count));
    }

    private static long componentBytes(ItemStack stack) {
        appeng.api.definitions.IMaterials m = AEApi.instance()
            .definitions()
            .materials();
        IItemDefinition[] definitions = { m.cell1kPart(), m.cell4kPart(), m.cell16kPart(), m.cell64kPart(),
            m.cell256kPart(), m.cell1024kPart(), m.cell4096kPart(), m.cell16384kPart() };
        long bytes = 1024;
        for (IItemDefinition definition : definitions) {
            if (definition.isSameAs(stack)) return bytes;
            bytes *= 4;
        }
        return 0;
    }
}
