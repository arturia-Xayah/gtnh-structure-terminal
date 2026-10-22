package dev.plasmaforgepower.ui;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.SyncHandler;

import dev.plasmaforgepower.api.ForgePowerAccess;
import dev.plasmaforgepower.core.PowerLimit;
import dev.plasmaforgepower.core.PowerNbt;
import dev.plasmaforgepower.core.PowerSettings;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/**
 * Bounded, server-authoritative edits. Stale edits to the same color are rejected instead of overwriting another
 * player.
 */
public final class PowerConfigSync extends SyncHandler<PowerConfigSync> {

    private final MTEMultiBlockBase forge;
    private final ForgePowerAccess access;
    private final PowerEditor editor;
    private PowerSettings last;
    private long lastPower = -1;
    private long lastVoltage = -1;
    private boolean lastFormed;

    public PowerConfigSync(MTEMultiBlockBase forge, PowerEditor editor) {
        this.forge = forge;
        this.access = (ForgePowerAccess) forge;
        this.editor = editor;
        allowC2S();
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        PowerSettings settings = access.pfp$getSettings();
        if (init || !settings.equals(last)
            || lastPower != access.pfp$ratedPower()
            || lastVoltage != access.pfp$ratedVoltage()
            || lastFormed != access.pfp$isFormed()) sendSnapshot(0, "", 0);
    }

    private void sendSnapshot(int id, String status, int changes) {
        last = access.pfp$getSettings();
        lastPower = access.pfp$ratedPower();
        lastVoltage = access.pfp$ratedVoltage();
        lastFormed = access.pfp$isFormed();
        syncToClient(id, buffer -> {
            buffer.writeNBTTagCompoundToBuffer(PowerNbt.write(last));
            buffer.writeLong(lastPower);
            buffer.writeLong(lastVoltage);
            buffer.writeBoolean(lastFormed);
            if (id == 1) {
                buffer.writeStringToBuffer(status);
                buffer.writeInt(changes);
            }
        });
    }

    @Override
    public void readOnClient(int id, PacketBuffer buffer) throws IOException {
        if (id != 0 && id != 1) return;
        NBTTagCompound tag = buffer.readNBTTagCompoundFromBuffer();
        PowerSettings settings = PowerNbt.read(tag);
        long power = buffer.readLong(), voltage = buffer.readLong();
        boolean formed = buffer.readBoolean();
        String message = id == 1 ? buffer.readStringFromBuffer(32) : "";
        int changes = id == 1 ? buffer.readInt() : 0;
        if (settings == null) return;
        editor.snapshot(settings, power, voltage, formed, "saved".equals(message) || "clamped".equals(message));
        if (id == 1) {
            editor.pending = false;
            editor.message = message;
            editor.changes = changes;
        }
    }

    public void save() {
        if (editor.pending || editor.dirtyCount() == 0) return;
        if (!editor.valid()) {
            editor.message = "invalid";
            return;
        }
        editor.beginSave();
        syncToServer(1, buffer -> {
            buffer.writeByte(editor.dirtyCount());
            for (int color = -1; color < 16; color++) if (editor.dirty(color)) {
                PowerLimit old = editor.saved.get(color);
                buffer.writeByte(color);
                buffer.writeLong(old.eut());
                buffer.writeByte(old.tier());
                buffer.writeStringToBuffer(editor.amps(color));
                buffer.writeByte(editor.tier(color));
            }
        });
    }

    @Override
    public void readOnServer(int id, PacketBuffer buffer) throws IOException {
        if (id != 1 || buffer.readableBytes() > 2048) return;
        EntityPlayer player = getSyncManager().getPlayer();
        IGregTechTileEntity tile = forge.getBaseMetaTileEntity();
        if (tile == null || !tile.isUseableByPlayer(player)) {
            sendSnapshot(1, "unavailable", 0);
            return;
        }
        int count = buffer.readUnsignedByte();
        if (count < 1 || count > 17) {
            sendSnapshot(1, "invalid", 0);
            return;
        }
        PowerSettings settings = access.pfp$getSettings();
        boolean[] seen = new boolean[17];
        String failure = "";
        for (int i = 0; i < count; i++) {
            int color = buffer.readByte();
            long expectedPower = buffer.readLong();
            int expectedTier = buffer.readUnsignedByte();
            String amps = buffer.readStringFromBuffer(32);
            int tier = buffer.readUnsignedByte();
            if (!PowerSettings.validColor(color) || seen[color + 1]) {
                failure = "invalid";
                continue;
            }
            seen[color + 1] = true;
            PowerLimit old = settings.get(color);
            if (old.eut() != expectedPower || old.tier() != expectedTier) {
                failure = "stale";
                continue;
            }
            try {
                // Preserve an exact clamped EU/t value when its rounded display is submitted without an actual edit.
                PowerLimit value = old.tier() == tier && old.amps()
                    .equals(amps) ? old : PowerLimit.parse(amps, tier);
                settings.set(color, value);
            } catch (IllegalArgumentException invalid) {
                failure = "invalid";
            }
        }
        if (buffer.readableBytes() != 0) failure = "invalid";
        if (!failure.isEmpty()) {
            sendSnapshot(1, failure, 0);
            return;
        }
        int changes = access.pfp$applySettings(settings);
        sendSnapshot(1, changes == 0 ? "saved" : "clamped", changes);
    }
}
