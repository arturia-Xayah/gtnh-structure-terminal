package dev.aestoragevault.ui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.FuzzyMode;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.IVirtualSlotHolder;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.sync.GuiBridge;
import appeng.util.Platform;
import dev.aestoragevault.world.TileOutput;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/** Native AE virtual-slot packets provide item/fluid NEI drag-and-drop without fake physical items. */
public final class OutputContainer extends AEBaseContainer implements IVirtualSlotHolder {

    public final TileOutput tile;
    private final IAEStack<?>[] clientSlots = new IAEStack<?>[TileOutput.FILTER_SLOTS];
    @GuiSync(40)
    public FuzzyMode fuzzy = FuzzyMode.IGNORE_ALL;
    @GuiSync(41)
    public int fuzzyCards;
    @GuiSync(42)
    public int oreCards;

    public OutputContainer(EntityPlayer player, TileOutput tile) {
        super(player.inventory, tile);
        this.tile = tile;
        for (int i = 0; i < 3; i++) addSlotToContainer(
            new SlotRestrictedInput(
                SlotRestrictedInput.PlacableItemType.UPGRADES,
                tile.upgrades,
                i,
                180,
                26 + i * 18,
                player.inventory));
        bindPlayerInventory(player.inventory, 0, 140);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return !tile.isInvalid() && player.worldObj == tile.getWorldObj()
            && player.worldObj.getTileEntity(tile.xCoord, tile.yCoord, tile.zCoord) == tile
            && player.getDistanceSq(tile.xCoord + .5, tile.yCoord + .5, tile.zCoord + .5) <= 64
            && (player.worldObj.isRemote || hasAccess(SecurityPermissions.BUILD, false));
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            fuzzy = tile.fuzzyMode();
            fuzzyCards = tile.getInstalledUpgrades(Upgrades.FUZZY);
            oreCards = tile.getInstalledUpgrades(Upgrades.ORE_FILTER);
            updateVirtualSlots(StorageName.CONFIG, tile.filters, clientSlots);
        }
        super.detectAndSendChanges();
    }

    @Override
    public void receiveSlotStacks(StorageName name, Int2ObjectMap<IAEStack<?>> stacks) {
        if (name != StorageName.CONFIG || stacks == null || stacks.size() > TileOutput.FILTER_SLOTS) return;
        if (Platform.isServer() && !canInteractWith(getInventoryPlayer().player)) return;
        for (Int2ObjectMap.Entry<IAEStack<?>> entry : stacks.int2ObjectEntrySet()) {
            int slot = entry.getIntKey();
            if (slot < 0 || slot >= TileOutput.FILTER_SLOTS) continue;
            IAEStack<?> stack = entry.getValue();
            if (stack != null && !(stack instanceof IAEItemStack) && !(stack instanceof IAEFluidStack)) continue;
            tile.filters.putAEStackInSlot(
                slot,
                stack == null ? null
                    : stack.copy()
                        .setStackSize(1));
        }
        if (Platform.isServer()) updateVirtualSlots(StorageName.CONFIG, tile.filters, clientSlots);
    }

    @Override
    public boolean enchantItem(EntityPlayer player, int action) {
        if (player.worldObj.isRemote || !canInteractWith(player)) return false;
        if (action == 0 && tile.getInstalledUpgrades(Upgrades.FUZZY) > 0) {
            FuzzyMode[] values = FuzzyMode.values();
            tile.getConfigManager()
                .putSetting(
                    Settings.FUZZY_MODE,
                    values[(tile.fuzzyMode()
                        .ordinal() + 1) % values.length]);
        } else if (action == 1 && tile.getInstalledUpgrades(Upgrades.ORE_FILTER) > 0)
            Platform.openGUI(player, tile, ForgeDirection.UNKNOWN, GuiBridge.GUI_ORE_FILTER);
        else return false;
        return true;
    }
}
