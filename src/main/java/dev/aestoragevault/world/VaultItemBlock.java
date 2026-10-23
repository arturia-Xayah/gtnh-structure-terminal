package dev.aestoragevault.world;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dev.aestoragevault.VaultMod;

/** Player-facing operating instructions travel with each controller/port item. */
public final class VaultItemBlock extends ItemBlock {

    public VaultItemBlock(Block block) {
        super(block);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        super.addInformation(stack, player, lines, advanced);
        String part = field_150939_a == VaultMod.CONTROLLER ? "controller"
            : field_150939_a == VaultMod.ACCESS ? "access" : "output";
        int count = part.equals("controller") ? 7 : part.equals("access") ? 5 : 8;
        for (int i = 1; i <= count; i++) lines.add(StatCollector.translateToLocal("vault.tooltip." + part + "." + i));
    }
}
