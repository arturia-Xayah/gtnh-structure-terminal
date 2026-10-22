package dev.aestoragevault.ae;

import java.io.IOException;
import java.util.Map;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import dev.aestoragevault.core.Resource;
import dev.aestoragevault.world.TileAccess;

public final class VaultInventory<T extends IAEStack<T>> implements IMEInventoryHandler<T> {

    private final TileAccess access;
    private final IAEStackType<T> type;
    private final String channel;

    public VaultInventory(TileAccess access, IAEStackType<T> type, String channel) {
        this.access = access;
        this.type = type;
        this.channel = channel;
    }

    @Override
    public T injectItems(T input, Actionable mode, BaseActionSource source) {
        if (input == null) return null;
        if (!access.available()) return input;
        try {
            long accepted = access.bound.store()
                .insert(ResourceCodec.encode(input), input.getStackSize(), mode == Actionable.SIMULATE);
            if (accepted == input.getStackSize()) return null;
            return input.copy()
                .setStackSize(input.getStackSize() - accepted);
        } catch (IOException | RuntimeException e) {
            access.bound.ioFailure(e);
            return input;
        }
    }

    @Override
    public T extractItems(T request, Actionable mode, BaseActionSource source) {
        if (request == null || !access.available()) return null;
        try {
            long taken = access.bound.store()
                .extract(ResourceCodec.encode(request), request.getStackSize(), mode == Actionable.SIMULATE);
            return taken == 0 ? null
                : request.copy()
                    .setStackSize(taken);
        } catch (IOException | RuntimeException e) {
            access.bound.ioFailure(e);
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public IItemList<T> getAvailableItems(IItemList<T> out, int iteration) {
        if (!access.available()) return out;
        try {
            for (Map.Entry<Resource, Long> e : access.bound.store()
                .entries()
                .entrySet()) if (e.getKey().channel.equals(channel)) {
                    T stack = (T) ResourceCodec.decode(e.getKey());
                    stack.setStackSize(e.getValue());
                    out.addStorage(stack);
                }
        } catch (IOException | RuntimeException e) {
            access.bound.ioFailure(e);
        }
        return out;
    }

    @Override
    public T getAvailableItem(T request, int iteration) {
        if (request == null || !access.available()) return null;
        try {
            long count = access.bound.store()
                .amount(ResourceCodec.encode(request));
            return count == 0 ? null
                : request.copy()
                    .setStackSize(count);
        } catch (IOException | RuntimeException e) {
            access.bound.ioFailure(e);
            return null;
        }
    }

    @Override
    public IAEStackType<T> getStackType() {
        return type;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(T input) {
        return false;
    }

    @Override
    public boolean canAccept(T input) {
        if (input == null || input.getStackSize() <= 0 || !access.available()) return false;
        try {
            return access.bound.store()
                .insert(ResourceCodec.encode(input), 1, true) > 0;
        } catch (IOException | RuntimeException e) {
            access.bound.ioFailure(e);
            return false;
        }
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(int pass) {
        return pass == 1 || pass == 2;
    }
}
