package dev.aeoptimizer.packtest;

import java.lang.reflect.Method;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.github.vfyjxf.nee.network.packet.PacketNEIPatternRecipe;
import com.github.vfyjxf.nee.utils.ItemUtils;

import appeng.api.AEApi;
import appeng.api.parts.IPartHost;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.util.item.AEItemStack;
import dev.aeoptimizer.mixin.NEIPatternPacketAccessor;
import dev.aeoptimizer.pattern.AssemblyLineRenamer;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.maps.FormingPressBackend;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Exercises the real transformed NEE handler, AE virtual slots and GT name-mold recipe. */
final class AssemblyLineRenamePackChecks {

    static PartPatternTerminal terminal;

    static void setup(OptimizerPackTest suite) {
        net.minecraft.block.Block block = AEApi.instance()
            .definitions()
            .blocks()
            .multiPart()
            .maybeBlock()
            .get();
        OptimizerPackTest.check(suite.world.setBlock(6, 80, 8, block, 0, 3), "terminal host placement");
        IPartHost host = (IPartHost) suite.world.getTileEntity(6, 80, 8);
        ItemStack cable = AEApi.instance()
            .definitions()
            .parts()
            .cableGlass()
            .stack(appeng.api.util.AEColor.Transparent, 1);
        host.addPart(cable, ForgeDirection.UNKNOWN, suite.player);
        host.addPart(
            AEApi.instance()
                .definitions()
                .parts()
                .patternTerminalEx()
                .maybeStack(1)
                .get(),
            ForgeDirection.NORTH,
            suite.player);
        terminal = (PartPatternTerminal) host.getPart(ForgeDirection.NORTH);
        verify(suite, "rename_terminal_placed", terminal != null);
    }

    static void noOptimizer(OptimizerPackTest suite) throws Exception {
        verify(
            suite,
            "rename_no_optimizer_unchanged",
            !importRecipe(suite, AssemblyLineRenamer.RECIPE_ID).getItemStackForNEI()
                .hasDisplayName());
    }

    static void enabled(OptimizerPackTest suite) throws Exception {
        verify(suite, "rename_default_off", !suite.optimizer.autoRename);
        verify(
            suite,
            "rename_disabled_unchanged",
            !importRecipe(suite, AssemblyLineRenamer.RECIPE_ID).getItemStackForNEI()
                .hasDisplayName());
        dev.aeoptimizer.ui.OptimizerContainer gui = new dev.aeoptimizer.ui.OptimizerContainer(suite.optimizer);
        verify(suite, "rename_gui_toggle", gui.enchantItem(suite.player, 1) && suite.optimizer.autoRename);
        NBTTagCompound saved = new NBTTagCompound();
        suite.optimizer.writeToNBT(saved);
        dev.aeoptimizer.network.TileOptimizer restored = new dev.aeoptimizer.network.TileOptimizer();
        restored.readFromNBT(saved);
        verify(suite, "rename_setting_persisted", restored.autoRename);
        gui.updateProgressBar(2, 1);
        verify(suite, "rename_gui_sync", gui.autoRename);
        suite.player.setPosition(100, 81, 100);
        verify(suite, "rename_gui_distance_guard", !gui.enchantItem(suite.player, 1) && suite.optimizer.autoRename);
        suite.player.setPosition(8, 81, 8);

        verify(
            suite,
            "rename_other_recipe_unchanged",
            !importRecipe(suite, "gt.recipe.assembler").getItemStackForNEI()
                .hasDisplayName());
        verify(
            suite,
            "rename_missing_source_unchanged",
            !importRecipe(suite, "").getItemStackForNEI()
                .hasDisplayName());
        IAEStack<?> renamed = importRecipe(suite, AssemblyLineRenamer.RECIPE_ID);
        verify(
            suite,
            "rename_second_input",
            "重命名1".equals(
                renamed.getItemStackForNEI()
                    .getDisplayName()));
        verify(suite, "rename_large_count", renamed.getStackSize() == 10000);
        IAEStack<?> first = terminal.getAEInventoryByName(StorageName.CRAFTING_INPUT)
            .getAEStackInSlot(0);
        IAEStack<?> third = terminal.getAEInventoryByName(StorageName.CRAFTING_INPUT)
            .getAEStackInSlot(2);
        verify(
            suite,
            "rename_first_and_third",
            !first.getItemStackForNEI()
                .hasDisplayName() && "重命名2".equals(
                    third.getItemStackForNEI()
                        .getDisplayName()));
        verify(
            suite,
            "rename_fluids_unchanged",
            terminal.getAEInventoryByName(StorageName.CRAFTING_INPUT)
                .getAEStackInSlot(3)
                .getStackSize() == 1000
                && terminal.getAEInventoryByName(StorageName.CRAFTING_INPUT)
                    .getAEStackInSlot(4)
                    .getStackSize() == 2000);
        verify(
            suite,
            "rename_output_unchanged",
            terminal.getAEInventoryByName(StorageName.CRAFTING_OUTPUT)
                .getAEStackInSlot(0)
                .getItemStackForNEI()
                .getItem() == Items.paper);

        ItemStack original = foil();
        ItemStack mold = ItemList.Shape_Mold_Name.get(1);
        mold.setStackDisplayName("重命名1");
        Method method = FormingPressBackend.class.getDeclaredMethod("findRenamingRecipe", ItemStack[].class);
        method.setAccessible(true);
        GTRecipe recipe = (GTRecipe) method
            .invoke(RecipeMaps.formingPressRecipes.getBackend(), (Object) new ItemStack[] { original, mold });
        verify(
            suite,
            "rename_matches_actual_forming_press",
            recipe != null && AEItemStack.create(recipe.mOutputs[0])
                .equals(renamed));
        verify(
            suite,
            "rename_nbt_matches_actual_forming_press",
            ItemStack.areItemStackTagsEqual(recipe.mOutputs[0], renamed.getItemStackForNEI()));
        verify(suite, "rename_mold_not_consumed", recipe.mInputs[0].stackSize == 0);
        RenameEncodingPackChecks.run(suite);
        suite.optimizer.autoRename = false;
    }

    static IAEStack<?> importRecipe(OptimizerPackTest suite, String source) throws Exception {
        NBTTagCompound input = new NBTTagCompound(), output = new NBTTagCompound();
        input.setString(AssemblyLineRenamer.SOURCE_KEY, source);
        for (int i = 0; i < 3; i++) input.setTag("#" + i, ItemUtils.writeItemStackToNBT(foil(), i == 1 ? 10000 : 64));
        input.setTag(
            "#3",
            ItemUtils.writeItemStackToNBT(
                GTUtility.getFluidDisplayStack(new FluidStack(FluidRegistry.WATER, 1000), true),
                1));
        input.setTag(
            "#4",
            ItemUtils.writeItemStackToNBT(
                GTUtility.getFluidDisplayStack(new FluidStack(FluidRegistry.WATER, 2000), true),
                1));
        output.setTag("Outputs0", ItemUtils.writeItemStackToNBT(new ItemStack(Items.paper), 1));
        PacketNEIPatternRecipe outbound = new PacketNEIPatternRecipe(input, output);
        PacketNEIPatternRecipe inbound = new PacketNEIPatternRecipe();
        ByteBuf buffer = Unpooled.buffer();
        try {
            outbound.toBytes(buffer);
            inbound.fromBytes(buffer);
        } finally {
            buffer.release();
        }
        OptimizerPackTest.check(
            source.equals(
                ((NEIPatternPacketAccessor) inbound).aeo$getInput()
                    .getString(AssemblyLineRenamer.SOURCE_KEY)),
            "recipe source lost over wire");
        ContainerPatternTerm container = new appeng.container.implementations.ContainerPatternTermEx(
            suite.player.inventory,
            terminal);
        net.minecraft.inventory.Container previous = suite.player.openContainer;
        suite.player.openContainer = container;
        try {
            java.lang.reflect.Constructor<cpw.mods.fml.common.network.simpleimpl.MessageContext> constructor = cpw.mods.fml.common.network.simpleimpl.MessageContext.class
                .getDeclaredConstructor(net.minecraft.network.INetHandler.class, cpw.mods.fml.relauncher.Side.class);
            constructor.setAccessible(true);
            cpw.mods.fml.common.network.simpleimpl.MessageContext context = constructor
                .newInstance(suite.player.playerNetServerHandler, cpw.mods.fml.relauncher.Side.SERVER);
            new PacketNEIPatternRecipe.Handler().onMessage(inbound, context);
            return terminal.getAEInventoryByName(StorageName.CRAFTING_INPUT)
                .getAEStackInSlot(1);
        } finally {
            container.onContainerClosed(suite.player);
            suite.player.openContainer = previous;
        }
    }

    private static ItemStack foil() {
        return GTOreDictUnificator.get(OrePrefixes.foil, Materials.Gallium, 1);
    }

    private static void verify(OptimizerPackTest suite, String name, boolean ok) {
        OptimizerPackTest.check(ok, name);
        suite.row(name, true);
    }
}
