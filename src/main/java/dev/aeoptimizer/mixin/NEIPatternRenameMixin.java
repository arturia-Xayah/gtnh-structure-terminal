package dev.aeoptimizer.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.vfyjxf.nee.network.packet.PacketNEIPatternRecipe;
import com.github.vfyjxf.nee.utils.ItemUtils;

import appeng.container.implementations.ContainerPatternTerm;
import codechicken.nei.recipe.StackInfo;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import dev.aeoptimizer.network.OptimizerNetworks;
import dev.aeoptimizer.network.TileOptimizer;
import dev.aeoptimizer.pattern.AssemblyLineRenamer;
import dev.aeoptimizer.pattern.RenamePatternEncoder;

@Mixin(value = PacketNEIPatternRecipe.Handler.class, remap = false)
public abstract class NEIPatternRenameMixin {

    @Inject(
        method = "onMessage(Lcom/github/vfyjxf/nee/network/packet/PacketNEIPatternRecipe;Lcpw/mods/fml/common/network/simpleimpl/MessageContext;)Lcpw/mods/fml/common/network/simpleimpl/IMessage;",
        at = @At("HEAD"),
        cancellable = true)
    private void aeo$renameInputs(PacketNEIPatternRecipe packet, MessageContext context,
        CallbackInfoReturnable<IMessage> ci) {
        if (((NEIPatternPacketAccessor) packet).aeo$getOutput() == null) return;
        if (!(context.getServerHandler().playerEntity.openContainer instanceof ContainerPatternTerm)) return;
        ContainerPatternTerm container = (ContainerPatternTerm) context.getServerHandler().playerEntity.openContainer;
        NBTTagCompound input = ((NEIPatternPacketAccessor) packet).aeo$getInput();
        if (input == null || !AssemblyLineRenamer.RECIPE_ID.equals(input.getString(AssemblyLineRenamer.SOURCE_KEY)))
            return;
        if (container.getNetworkNode() == null) return;
        TileOptimizer optimizer = OptimizerNetworks.find(
            container.getNetworkNode()
                .getGrid());
        if (optimizer == null || !optimizer.autoRename) return;
        int size = container.getPatternInputsWidth() * container.getPatternInputsHeigh()
            * container.getPatternInputPages();
        ItemStack[] items = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            if (!input.hasKey("#" + i, 10)) continue;
            ItemStack stack = ItemUtils.loadItemStackFromNBT(input.getCompoundTag("#" + i));
            if (stack != null && !StackInfo.isFluidDisplayItem(stack)) items[i] = stack;
        }
        ItemStack[] renamed = AssemblyLineRenamer.rename(items);
        if (optimizer.autoEncodeRename && !RenamePatternEncoder.generate(container, items, renamed)) {
            ci.cancel();
            return;
        }
        for (int i = 0; i < size; i++) {
            if (renamed[i] == items[i]) continue;
            // Preserve NEE's integer Count and every other serialized field verbatim.
            input.getCompoundTag("#" + i)
                .setTag("tag", renamed[i].getTagCompound());
        }
    }
}
