package dev.structureterminal;

import java.util.Map;
import java.util.TreeMap;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;

import dev.structureterminal.core.Limits;

public final class TerminalSettings {

    public int size = 1;
    public boolean mirror;
    public boolean network;
    public final Map<String, Integer> channels = new TreeMap<String, Integer>();
    /** Group IDs are server-recomputed candidate-set fingerprints; values are registry item keys. */
    public final Map<String, String> choices = new TreeMap<String, String>();

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("size", size);
        tag.setBoolean("mirror", mirror);
        tag.setBoolean("network", network);
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
        if (s.size < 1 || s.size > 64) throw new IllegalArgumentException("Size must be 1..64");
        s.mirror = tag.getBoolean("mirror");
        s.network = tag.getBoolean("network");
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
