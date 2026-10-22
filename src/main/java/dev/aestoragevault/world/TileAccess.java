package dev.aestoragevault.world;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.AccessRestriction;
import appeng.api.networking.GridFlags;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;
import dev.aestoragevault.VaultMod;
import dev.aestoragevault.ae.ResourceCodec;
import dev.aestoragevault.ae.VaultInventory;
import dev.aestoragevault.core.Resource;

public class TileAccess extends AENetworkTile implements ICellContainer {

    public TileController bound;
    private final VaultInventory<IAEItemStack> items = new VaultInventory<>(
        this,
        appeng.util.item.AEItemStackType.ITEM_STACK_TYPE,
        "items");
    private final VaultInventory<IAEFluidStack> fluids = new VaultInventory<>(
        this,
        appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE,
        "fluids");

    @Override
    protected AENetworkProxy createProxy() {
        AENetworkProxy proxy = new AENetworkProxy(this, "proxy", new ItemStack(VaultMod.ACCESS), true);
        proxy.setFlags(GridFlags.REQUIRE_CHANNEL);
        proxy.setIdlePowerUsage(2);
        proxy.setValidSides(EnumSet.allOf(ForgeDirection.class));
        return proxy;
    }

    public void bind(TileController owner) {
        bound = owner;
        refresh();
    }

    public void unbind(TileController owner) {
        if (bound == owner) {
            bound = null;
            refresh();
        }
    }

    public boolean available() {
        return bound != null && bound.available() && getProxy().isActive();
    }

    @Override
    public List<IMEInventoryHandler> getCellArray(IAEStackType<?> type) {
        if (!available()) return Collections.emptyList();
        if (type == appeng.util.item.AEItemStackType.ITEM_STACK_TYPE)
            return Collections.<IMEInventoryHandler>singletonList(items);
        if (type == appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE)
            return Collections.<IMEInventoryHandler>singletonList(fluids);
        return Collections.emptyList();
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public AccessRestriction getReshuffleAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public void saveChanges(IMEInventory inventory) {
        markDirty();
    }

    @MENetworkEventSubscribe
    public void powerChanged(MENetworkPowerStatusChange event) {
        refresh();
    }

    @MENetworkEventSubscribe
    public void channelsChanged(MENetworkChannelsChanged event) {
        refresh();
    }

    public void refresh() {
        try {
            getProxy().getGrid()
                .postEvent(new MENetworkCellArrayUpdate());
        } catch (GridAccessException ignored) { /* A disconnected grid will discover storage when it reconnects. */ }
    }

    @TileEvent(TileEventType.TICK)
    public void watchStructure() {
        if (worldObj == null || worldObj.isRemote) return;
        if (worldObj.getTotalWorldTime() % 20 == 0 && bound != null && !bound.available()) unbind(bound);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void notifyImport(Map<Resource, Long> imported) {
        if (!available()) return;
        try {
            for (IAEStackType type : new IAEStackType[] { appeng.util.item.AEItemStackType.ITEM_STACK_TYPE,
                appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE }) {
                List<IAEStack<?>> changes = new ArrayList<>();
                String channel = type == appeng.util.item.AEItemStackType.ITEM_STACK_TYPE ? "items" : "fluids";
                for (Map.Entry<Resource, Long> e : imported.entrySet()) if (e.getKey().channel.equals(channel)) {
                    IAEStack stack = ResourceCodec.decode(e.getKey());
                    stack.setStackSize(e.getValue());
                    changes.add(stack);
                }
                getProxy().getStorage()
                    .postAlterationOfStoredItems(type, changes, new MachineSource(this));
            }
        } catch (GridAccessException ignored) { /* Next grid scan reads the complete committed inventory. */ } catch (IOException e) {
            bound.ioFailure(e);
        }
    }
}
