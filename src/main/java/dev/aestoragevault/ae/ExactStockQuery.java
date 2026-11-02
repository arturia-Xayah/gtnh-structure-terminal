package dev.aestoragevault.ae;

import java.math.BigInteger;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import dev.aestoragevault.world.TileController;

/** On-demand exact quantity; no periodic formatting or additional sync packets. */
public final class ExactStockQuery {

    private ExactStockQuery() {}

    public static void send(TileController controller, EntityPlayer player, ItemStack held) {
        if (!controller.available()) {
            player.addChatMessage(new ChatComponentTranslation("vault.quantity.unavailable"));
            return;
        }
        try {
            report(controller, player, AEItemStack.create(held), "vault.quantity.item");
            FluidStack fluid = gregtech.api.util.GTUtility.getFluidForFilledItem(held, true);
            if (fluid != null) report(controller, player, AEFluidStack.create(fluid), "vault.quantity.fluid");
        } catch (Exception failure) {
            controller.ioFailure(failure);
            player.addChatMessage(new ChatComponentTranslation("vault.quantity.unavailable"));
        }
    }

    private static void report(TileController controller, EntityPlayer player, IAEStack<?> stack, String key)
        throws Exception {
        BigInteger amount = controller.store()
            .exactAmount(ResourceCodec.encode(stack));
        player.addChatMessage(
            new ChatComponentTranslation(
                key,
                stack.getDisplayName(),
                amount.toString(),
                amount.min(BigInteger.valueOf(Long.MAX_VALUE))
                    .toString()));
    }
}
