package dev.structureterminal.pattern;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.oredict.ShapedOreRecipe;

import appeng.api.AEApi;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import cpw.mods.fml.common.registry.GameRegistry;

/** A physical pattern inventory, deliberately NOT an ICraftingPatternItem (one item is not one recipe). */
public final class IntegratedPattern extends Item {

    public static final IntegratedPattern ITEM = new IntegratedPattern();
    public static final int SIZE = 36;
    public static final int GUI = 36;
    private static final String CONTENTS = "IntegratedPatterns";

    private IntegratedPattern() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabTools);
        setUnlocalizedName("structureterminal.integrated_pattern");
        setTextureName("structureterminal:integrated_pattern");
    }

    public static void register() {
        GameRegistry.registerItem(ITEM, "integrated_pattern");
    }

    public static void recipe() {
        Item dual = GameRegistry.findItem("ae2fc", "fluid_interface");
        if (dual == null) throw new IllegalStateException("Integrated pattern requires ae2fc:fluid_interface");
        GameRegistry.addRecipe(
            new ShapedOreRecipe(
                new ItemStack(ITEM),
                "BDB",
                "DCD",
                "BDB",
                'B',
                AEApi.instance()
                    .definitions()
                    .materials()
                    .blankPattern()
                    .maybeStack(1)
                    .get(),
                'D',
                new ItemStack(dual),
                'C',
                gregtech.api.enums.OrePrefixes.circuit.get(gregtech.api.enums.Materials.IV)
                    .toString()));
    }

    public static boolean is(ItemStack stack) {
        return stack != null && stack.getItem() == ITEM;
    }

    public static ICraftingPatternDetails details(ItemStack stack, World world) {
        if (stack == null || !(stack.getItem() instanceof ICraftingPatternItem)) return null;
        try {
            return ((ICraftingPatternItem) stack.getItem()).getPatternForItem(stack, world);
        } catch (RuntimeException invalidPattern) {
            return null;
        }
    }

    public static ItemStack[] read(ItemStack holder) {
        ItemStack[] result = new ItemStack[SIZE];
        if (!is(holder) || !holder.hasTagCompound()) return result;
        NBTTagList list = holder.getTagCompound()
            .getTagList(CONTENTS, 10);
        for (int i = 0; i < Math.min(SIZE, list.tagCount()); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            int slot = tag.getInteger("Slot");
            if (slot >= 0 && slot < SIZE && result[slot] == null) {
                ItemStack child = ItemStack.loadItemStackFromNBT(tag);
                if (child != null && !is(child)) {
                    child.stackSize = 1;
                    result[slot] = child;
                }
            }
        }
        return result;
    }

    public static void write(ItemStack holder, ItemStack[] items) {
        if (!is(holder) || items.length != SIZE) throw new IllegalArgumentException("Invalid pattern inventory");
        NBTTagList list = new NBTTagList();
        for (int slot = 0; slot < SIZE; slot++) {
            ItemStack item = items[slot];
            if (item == null) continue;
            if (is(item) || item.stackSize != 1) throw new IllegalArgumentException("Nested or stacked patterns");
            NBTTagCompound tag = item.writeToNBT(new NBTTagCompound());
            tag.setInteger("Slot", slot);
            list.appendTag(tag);
        }
        if (!holder.hasTagCompound()) holder.setTagCompound(new NBTTagCompound());
        if (list.tagCount() == 0) holder.getTagCompound()
            .removeTag(CONTENTS);
        else holder.getTagCompound()
            .setTag(CONTENTS, list);
    }

    public static int count(ItemStack holder) {
        int count = 0;
        for (ItemStack child : read(holder)) if (child != null) count++;
        return count;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) IntegratedPatternOpening.INSTANCE.request(player);
        return stack;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(StatCollector.translateToLocalFormatted("structureterminal.integrated.count", count(stack), SIZE));
        lines.add(StatCollector.translateToLocal("structureterminal.integrated.use"));
        lines.add(StatCollector.translateToLocal("structureterminal.integrated.install"));
        lines.add(StatCollector.translateToLocal("structureterminal.integrated.edit"));
        lines.add(StatCollector.translateToLocal("structureterminal.integrated.nesting"));
    }
}
