package dev.aeoptimizer.pattern;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.parts.IPatternTerminal;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

/** Generates real 1:1 processing patterns, paying for blanks before committing local inventory changes. */
public final class RenamePatternEncoder {

    private RenamePatternEncoder() {}

    public static boolean generate(ContainerPatternTerm container, ItemStack[] original, ItemStack[] renamed) {
        EntityPlayer player = container.getPlayerInv().player;
        IGrid grid = container.getNetworkNode()
            .getGrid();
        ISecurityGrid security = grid.getCache(ISecurityGrid.class);
        if (!container.canInteractWith(player) || security == null
            || !security.hasPermission(player, SecurityPermissions.BUILD)) {
            return fail(player, "permission");
        }
        ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
        IInventory patternSlots = container.getPatternTerminal()
            .getInventoryByName("pattern");
        List<ItemStack> generated = new ArrayList<>();
        int skipped = 0;
        for (int i = 0; i < original.length; i++) {
            if (original[i] == renamed[i]) continue;
            boolean exists = false;
            if (crafting != null) {
                for (ICraftingPatternDetails details : crafting
                    .getCraftingFor(AEItemStack.create(renamed[i]), null, 0, player.worldObj)) {
                    if (matches(details, original[i], renamed[i])) {
                        exists = true;
                        break;
                    }
                }
            }
            if (!exists) exists = present(player.inventory.mainInventory, original[i], renamed[i], player.worldObj)
                || matches(patternSlots.getStackInSlot(1), original[i], renamed[i], player.worldObj)
                || present(generated.toArray(new ItemStack[0]), original[i], renamed[i], player.worldObj);
            if (exists) skipped++;
            else generated.add(encode(original[i], renamed[i], player));
        }
        if (generated.isEmpty()) {
            if (skipped > 0) message(player, "done", 0, skipped);
            return true;
        }
        // GT's assembly line has at most 16 item inputs. Also bounds refund space and hostile packet work.
        if (generated.size() > 16) return fail(player, "limit");
        ItemStack blank = IPatternTerminal.createBlankPattern()
            .getItemStack();
        ItemStack terminalBlank = patternSlots.getStackInSlot(0);
        BlankPlan plan = plan(player.inventory.mainInventory, terminalBlank, blank, generated);
        if (plan == null) return fail(player, "space");
        if (plan.networkNeeded > 0) {
            // Reserve the terminal blank slot for a lossless refund if an external ME inventory returns fewer
            // items than its simulation promised. Do not overwrite named blanks or any other existing item.
            if (terminalBlank != null && !same(blank, terminalBlank)) return fail(player, "blanks");
            if (container.getItemMonitor() == null || container.getPowerSource() == null
                || !security.hasPermission(player, SecurityPermissions.EXTRACT)) return fail(player, "blanks");
            IAEItemStack request = IPatternTerminal.createBlankPattern()
                .setStackSize(plan.networkNeeded);
            IAEItemStack simulated = Platform.poweredExtraction(
                container.getPowerSource(),
                container.getItemMonitor(),
                request,
                container.getActionSource(),
                Actionable.SIMULATE);
            if (simulated == null || simulated.getStackSize() < plan.networkNeeded) return fail(player, "blanks");
            IAEItemStack taken = Platform.poweredExtraction(
                container.getPowerSource(),
                container.getItemMonitor(),
                request,
                container.getActionSource());
            if (taken == null || taken.getStackSize() < plan.networkNeeded) {
                if (taken != null && taken.getStackSize() > 0) {
                    ItemStack refund = blank.copy();
                    refund.stackSize = (terminalBlank == null ? 0 : terminalBlank.stackSize)
                        + (int) taken.getStackSize();
                    patternSlots.setInventorySlotContents(0, refund);
                    patternSlots.markDirty();
                    container.detectAndSendChanges();
                }
                return fail(player, "blanks");
            }
        }
        // No callbacks/remote operations between these local writes. Never use vanilla's creative-mode
        // addItemStackToInventory fallback, which may discard items when the inventory is full.
        for (int i = 0; i < plan.inventory.length; i++) {
            // Wireless terminal containers retain their held stack; do not replace unrelated inventory objects.
            if (!ItemStack.areItemStacksEqual(player.inventory.mainInventory[i], plan.inventory[i]))
                player.inventory.mainInventory[i] = plan.inventory[i];
        }
        patternSlots.setInventorySlotContents(0, plan.terminalBlank);
        patternSlots.markDirty();
        player.inventory.markDirty();
        container.detectAndSendChanges();
        message(player, "done", generated.size(), skipped);
        return true;
    }

    public static ItemStack encode(ItemStack input, ItemStack output, EntityPlayer player) {
        ItemStack pattern = AEApi.instance()
            .definitions()
            .items()
            .encodedUltimatePattern()
            .maybeStack(1)
            .get();
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList in = new NBTTagList(), out = new NBTTagList();
        in.appendTag(
            AEItemStack.create(input)
                .setStackSize(1)
                .toNBTGeneric());
        out.appendTag(
            AEItemStack.create(output)
                .setStackSize(1)
                .toNBTGeneric());
        tag.setTag("in", in);
        tag.setTag("out", out);
        tag.setBoolean("substitute", false);
        tag.setBoolean("beSubstitute", false);
        tag.setString("author", player.getCommandSenderName());
        tag.setLong("encodedAt", System.currentTimeMillis());
        pattern.setTagCompound(tag);
        return pattern;
    }

    public static boolean matches(ICraftingPatternDetails pattern, ItemStack input, ItemStack output) {
        if (pattern == null || pattern.isCraftable() || pattern.canSubstitute()) return false;
        IAEStack<?>[] in = pattern.getCondensedAEInputs(), out = pattern.getCondensedAEOutputs();
        // Only 1:1 unit patterns can satisfy every future order without rounding or extra outputs.
        return in.length == 1 && out.length == 1 && exact(in[0], input) && exact(out[0], output);
    }

    private static boolean exact(IAEStack<?> stack, ItemStack item) {
        return stack != null && stack.getStackSize() == 1 && stack.equals(AEItemStack.create(item));
    }

    private static boolean present(ItemStack[] patterns, ItemStack input, ItemStack output, World world) {
        for (ItemStack pattern : patterns) {
            if (matches(pattern, input, output, world)) return true;
            if (dev.structureterminal.pattern.IntegratedPattern.is(pattern)
                && present(dev.structureterminal.pattern.IntegratedPattern.read(pattern), input, output, world))
                return true;
        }
        return false;
    }

    private static boolean matches(ItemStack pattern, ItemStack input, ItemStack output, World world) {
        if (pattern == null || !(pattern.getItem() instanceof ICraftingPatternItem)) return false;
        try {
            return matches(((ICraftingPatternItem) pattern.getItem()).getPatternForItem(pattern, world), input, output);
        } catch (RuntimeException invalidPattern) {
            return false;
        }
    }

    public static final class BlankPlan {

        public final ItemStack[] inventory;
        public final ItemStack terminalBlank;
        public final int networkNeeded;

        private BlankPlan(ItemStack[] inventory, ItemStack terminalBlank, int networkNeeded) {
            this.inventory = inventory;
            this.terminalBlank = terminalBlank;
            this.networkNeeded = networkNeeded;
        }
    }

    /** Simulates every local change, including space freed by consuming a stack of blank patterns. */
    public static BlankPlan plan(ItemStack[] inventory, ItemStack terminal, ItemStack blank, List<ItemStack> outputs) {
        ItemStack[] copy = new ItemStack[inventory.length];
        for (int i = 0; i < copy.length; i++) copy[i] = inventory[i] == null ? null : inventory[i].copy();
        ItemStack remainder = terminal == null ? null : terminal.copy();
        int needed = outputs.size();
        if (same(remainder, blank)) {
            int used = Math.min(needed, Math.max(0, remainder.stackSize));
            needed -= used;
            remainder.stackSize -= used;
            if (remainder.stackSize <= 0) remainder = null;
        }
        for (int i = 0; i < copy.length && needed > 0; i++) {
            if (!same(copy[i], blank)) continue;
            int used = Math.min(needed, Math.max(0, copy[i].stackSize));
            needed -= used;
            copy[i].stackSize -= used;
            if (copy[i].stackSize <= 0) copy[i] = null;
        }
        for (ItemStack output : outputs) {
            boolean inserted = false;
            for (int i = 0; i < copy.length; i++) {
                if (copy[i] != null && copy[i].stackSize > 0) continue;
                copy[i] = output.copy();
                inserted = true;
                break;
            }
            if (!inserted) return null;
        }
        return new BlankPlan(copy, remainder, needed);
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return a != null && b != null && a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    private static boolean fail(EntityPlayer player, String reason) {
        message(player, reason);
        return false;
    }

    private static void message(EntityPlayer player, String key, Object... args) {
        player.addChatMessage(new ChatComponentTranslation("aeo.rename.encode." + key, args));
    }
}
