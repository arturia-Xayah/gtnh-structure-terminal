package dev.structureterminal.packing;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.oredict.ShapedOreRecipe;

import appeng.api.AEApi;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;

/** 1.7.10 adaptation of ExtendedAE's ME Packing Tape / Packaged Device. */
public final class PackingItems extends Item {

    public static final PackingItems TAPE = new PackingItems(false);
    public static final PackingItems PACKAGE = new PackingItems(true);
    private final boolean packed;

    private PackingItems(boolean packed) {
        this.packed = packed;
        setMaxStackSize(1);
        setUnlocalizedName("structureterminal." + (packed ? "package" : "me_packing_tape"));
        setTextureName("structureterminal:" + (packed ? "package" : "me_packing_tape"));
        if (!packed) {
            setMaxDamage(64);
            setCreativeTab(CreativeTabs.tabTools);
        }
    }

    public static void register() {
        GameRegistry.registerItem(TAPE, "me_packing_tape");
        GameRegistry.registerItem(PACKAGE, "package");
    }

    public static void recipe() {
        GameRegistry.addRecipe(
            new ShapedOreRecipe(
                new ItemStack(TAPE),
                "ABC",
                "BDB",
                "CBA",
                'A',
                AEApi.instance()
                    .definitions()
                    .materials()
                    .fluixDust()
                    .maybeStack(1)
                    .get(),
                'B',
                ItemList.Duct_Tape.get(1),
                'C',
                Items.slime_ball,
                'D',
                "dustEnderPearl"));
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hx, float hy, float hz) {
        // Return false client-side so Forge sends its ordinary use-item packet to the server.
        if (world.isRemote) return false;
        return packed ? PackingService.unpack(stack, player, world, x, y, z, side)
            : player.isSneaking() && PackingService.pack(stack, player, world, x, y, z, side, hx, hy, hz);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hx,
        float hy, float hz) {
        // The authoritative operation is performed by onItemUseFirst, never twice.
        return world.isRemote && (packed || player.isSneaking());
    }

    @Override
    public boolean doesSneakBypassUse(World world, int x, int y, int z, EntityPlayer player) {
        return true;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        if (packed) {
            ItemStack device = PackingService.device(stack);
            lines.add(
                device == null ? StatCollector.translateToLocal("structureterminal.packing.invalid")
                    : StatCollector
                        .translateToLocalFormatted("structureterminal.packing.contains", device.getDisplayName()));
            lines.add(StatCollector.translateToLocal("structureterminal.packing.unpack"));
        } else {
            lines.add(StatCollector.translateToLocal("structureterminal.packing.pack"));
            lines.add(StatCollector.translateToLocal("structureterminal.packing.contents"));
            lines.add(
                StatCollector.translateToLocalFormatted("structureterminal.packing.uses", 64 - stack.getItemDamage()));
        }
    }
}
