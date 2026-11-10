package dev.structureterminal;

import java.util.Map;
import java.util.TreeMap;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;

import dev.structureterminal.core.Limits;

public final class TerminalSettings {

    public static final int BUILD = 0, REPLACE = 1, DISMANTLE = 2;
    public int operation;

    public int size = 1;
    public boolean mirror;
    public boolean network;
    public int coilTier;
    public int glassTier = -1;
    public int repeatCount;
    public boolean noHatches = true;
    public boolean replaceCoils;
    public boolean keepHatches = true;
    public final Map<String, Integer> channels = new TreeMap<String, Integer>();
    /** Group IDs are server-recomputed candidate-set fingerprints; values are registry item keys. */
    public final Map<String, String> choices = new TreeMap<String, String>();

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("size", size);
        tag.setBoolean("mirror", mirror);
        tag.setBoolean("network", network);
        tag.setInteger("CoilTier", coilTier);
        tag.setInteger("GlassTier", glassTier);
        tag.setInteger("RepeatCount", repeatCount);
        tag.setInteger("NoHatchMode", noHatches ? 1 : 0);
        tag.setInteger("ReplaceCoilMode", replaceCoils ? 1 : 0);
        tag.setInteger("Operation", operation);
        tag.setBoolean("KeepHatches", keepHatches);
        NBTTagCompound cs = new NBTTagCompound(), ps = new NBTTagCompound();
        for (Map.Entry<String, Integer> e : channels.entrySet()) cs.setInteger(e.getKey(), e.getValue());
        for (Map.Entry<String, String> e : choices.entrySet()) ps.setString(e.getKey(), e.getValue());
        tag.setTag("channels", cs);
        tag.setTag("choices", ps);
        return tag;
    }

    public static TerminalSettings read(NBTTagCompound tag) {
        TerminalSettings s = new TerminalSettings();
        if (tag == null) return s;
        s.size = tag.hasKey("size") ? tag.getInteger("size") : 1;
        if (s.size < 1 || s.size > 99) throw new IllegalArgumentException("Size must be 1..99");
        s.mirror = tag.getBoolean("mirror");
        s.network = tag.getBoolean("network");
        s.coilTier = tag.getInteger("CoilTier");
        s.glassTier = tag.hasKey("GlassTier") ? tag.getInteger("GlassTier") : -1;
        if (s.glassTier < -1 || s.glassTier > 15) throw new IllegalArgumentException("Invalid glass tier");
        s.repeatCount = tag.hasKey("RepeatCount") ? tag.getInteger("RepeatCount") : (s.size == 1 ? 0 : s.size);
        if (s.coilTier < 0 || s.coilTier > 14 || s.repeatCount < 0 || s.repeatCount > 99)
            throw new IllegalArgumentException("Invalid advanced terminal settings");
        s.noHatches = !tag.hasKey("NoHatchMode") || tag.getInteger("NoHatchMode") != 0;
        s.replaceCoils = tag.getInteger("ReplaceCoilMode") != 0;
        s.keepHatches = !tag.hasKey("KeepHatches") || tag.getBoolean("KeepHatches");
        s.operation = tag.hasKey("Operation") ? tag.getInteger("Operation") : (s.replaceCoils ? REPLACE : BUILD);
        if (s.operation < BUILD || s.operation > DISMANTLE)
            throw new IllegalArgumentException("Invalid terminal operation");
        NBTTagCompound cs = tag.getCompoundTag("channels"), ps = tag.getCompoundTag("choices");
        if (cs.func_150296_c()
            .size() > Limits.CHANNELS
            || ps.func_150296_c()
                .size() > Limits.CHOICES) {
            throw new IllegalArgumentException("Too many settings");
        }
        for (Object key : cs.func_150296_c()) {
            String name = Limits.channel((String) key);
            s.channels.put(name, Limits.value(cs.getInteger(name)));
        }
        for (Object key : ps.func_150296_c()) {
            String name = (String) key, value = ps.getString(name);
            if (!name.matches("[a-f0-9]{64}") || value.length() > 512)
                throw new IllegalArgumentException("Invalid choice");
            s.choices.put(name, value);
        }
        return s;
    }

    public ItemStack trigger() {
        ItemStack trigger = new ItemStack(StructureTerminal.item, size);
        for (Map.Entry<String, Integer> e : channels.entrySet())
            ChannelDataAccessor.setChannelData(trigger, e.getKey(), e.getValue());
        return trigger;
    }

    public boolean replacing() {
        // Preserve the former coil-only switch for existing tools and callers.
        return operation == REPLACE || operation == BUILD && replaceCoils;
    }

    /** GT's native builder requires an explicit opt-in subchannel before it will place non-exclusive hatches. */
    public void enableHatchesForSelection(ItemStack selected) {
        if (selected == null) return;
        if (!(selected.getItem() instanceof net.minecraft.item.ItemBlock)) return;
        net.minecraft.block.Block block = ((net.minecraft.item.ItemBlock) selected.getItem()).field_150939_a;
        if (block != null && block.hasTileEntity(selected.getItemDamage()) && !channels.containsKey("gt_hatch"))
            channels.put("gt_hatch", 1);
    }

    public static TerminalSettings fromTool(ItemStack stack) {
        if (stack == null) return new TerminalSettings();
        try {
            return read(
                stack.hasTagCompound() ? stack.getTagCompound()
                    .getCompoundTag("terminal") : null);
        } catch (IllegalArgumentException e) {
            return new TerminalSettings();
        }
    }

    public void save(ItemStack stack) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setTag("terminal", write());
    }
}
