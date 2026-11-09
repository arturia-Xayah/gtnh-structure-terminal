package dev.structureterminal.server;

import java.util.Collections;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.config.Actionable;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import dev.structureterminal.TerminalSettings;
import dev.structureterminal.bridge.NativeBridge;

/** Uses a real AE monitor; wireless discovery is deliberately outside this test seam. */
public final class AeReturnChecks {

    public static void build(NativeBridge bridge, EntityPlayerMP player, TerminalSettings settings,
        IMEMonitor<IAEItemStack> monitor, String mode) {
        AeReturn.Sink sink = new AeReturn.Sink() {

            public boolean powered(long n) {
                return !mode.endsWith("unpowered");
            }

            public boolean charge(long n) {
                return powered(n);
            }

            public IAEItemStack insert(IAEItemStack s, Actionable action) {
                return mode.endsWith("full") ? s : monitor.injectItems(s, action, new PlayerSource(player, null));
            }
        };
        AdvancedBuild.build(
            bridge,
            player,
            settings,
            p -> mode.endsWith("unlinked") ? null : new AeReturn(p, p.getHeldItem(), sink));
    }

    public static void journal(EntityPlayerMP player) {
        ItemStack tool = new ItemStack(dev.structureterminal.StructureTerminal.item);
        final long[] received = { 0 };
        final boolean[] reject = { false };
        AeReturn.Sink sink = new AeReturn.Sink() {

            public boolean powered(long n) {
                return true;
            }

            public boolean charge(long n) {
                return true;
            }

            public IAEItemStack insert(IAEItemStack s, Actionable action) {
                if (reject[0]) return s;
                if (action == Actionable.SIMULATE) return null;
                long n = Math.min(3, s.getStackSize());
                received[0] += n;
                reject[0] = true; // Network disappears after a partial commit.
                return n == s.getStackSize() ? null
                    : s.copy()
                        .setStackSize(s.getStackSize() - n);
            }
        };
        AeReturn returns = new AeReturn(player, tool, sink);
        ItemStack input = new ItemStack(net.minecraft.init.Items.iron_ingot, 10);
        check(returns.canAccept(Collections.singletonList(input)), "preflight rejected");
        returns.stage(Collections.singletonList(input));
        check(!returns.flush() && received[0] == 3 && AeReturn.pending(tool), "partial return journal");
        tool = ItemStack.loadItemStackFromNBT(tool.writeToNBT(new NBTTagCompound()));
        check(AeReturn.pending(tool), "journal did not survive NBT reload");
        returns = new AeReturn(player, tool, sink);
        for (int i = 0; i < 3; i++) {
            reject[0] = false;
            returns.flush();
        }
        check(!AeReturn.pending(tool) && received[0] == 10, "retry duplicated/lost materials");
        check(returns.flush() && received[0] == 10, "empty retry duplicated materials");
        returns.stage(Collections.singletonList(input));
        returns.cancel();
        check(!AeReturn.pending(tool), "cancel left journal");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
