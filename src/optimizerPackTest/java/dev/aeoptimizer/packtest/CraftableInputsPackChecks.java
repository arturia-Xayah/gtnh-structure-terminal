package dev.aeoptimizer.packtest;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import dev.structureterminal.ae.CraftableInputs;

/** Real AE mixed-list and exact-identity checks; never loads any client GUI class. */
final class CraftableInputsPackChecks {

    static void run(OptimizerPackTest suite) {
        IItemList<IAEStack<?>> list = AEApi.instance()
            .storage()
            .createAEStackList();
        IAEStack<?> input = AEItemStack.create(new ItemStack(Items.iron_ingot));
        verify(suite, "badge_empty_network", !lookup(list, input));
        list.addStorage(
            input.copy()
                .setStackSize(10000));
        verify(suite, "badge_stock_without_pattern", !lookup(list, input));
        list.addCrafting(input);
        verify(suite, "badge_stock_with_pattern", lookup(list, input));
        list.findPrecise(input)
            .setStackSize(0);
        verify(suite, "badge_zero_stock_pattern", lookup(list, input));
        verify(suite, "badge_input_not_modified", !input.isCraftable() && input.getStackSize() == 1);
        verify(suite, "badge_unpowered", !CraftableInputs.isCraftable(false, list, input));
        verify(suite, "badge_null_input", !lookup(list, null));
        verify(suite, "badge_no_inventory", !CraftableInputs.isCraftable(true, null, input));

        IAEStack<?> tagged = taggedItem("A");
        list.addCrafting(tagged);
        verify(suite, "badge_exact_item_nbt", lookup(list, taggedItem("A")) && !lookup(list, taggedItem("B")));
        list.addCrafting(AEItemStack.create(new ItemStack(Items.dye, 1, 1)));
        verify(
            suite,
            "badge_exact_item_metadata",
            lookup(list, AEItemStack.create(new ItemStack(Items.dye, 1, 1)))
                && !lookup(list, AEItemStack.create(new ItemStack(Items.dye, 1, 0))));
        verify(suite, "badge_different_item", !lookup(list, AEItemStack.create(new ItemStack(Items.gold_ingot))));

        IAEStack<?> fluid = AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1000));
        list.addCrafting(fluid);
        verify(suite, "badge_fluid_zero_stock", lookup(list, fluid));
        list.addStorage(
            fluid.copy()
                .setStackSize(64000));
        verify(
            suite,
            "badge_fluid_in_stock",
            lookup(
                list,
                fluid.copy()
                    .setStackSize(1)));
        verify(
            suite,
            "badge_other_fluid",
            !lookup(list, AEFluidStack.create(new FluidStack(FluidRegistry.LAVA, 1000))));
        IAEStack<?> taggedFluid = taggedFluid("A");
        list.addCrafting(taggedFluid);
        // AEFluidStack.create in this pinned AE version drops custom fluid NBT. Follow its identity,
        // rather than inventing a distinction the synchronized network record cannot represent.
        verify(
            suite,
            "badge_native_fluid_identity",
            !taggedFluid.hasTagCompound() && lookup(list, taggedFluid("A")) && lookup(list, taggedFluid("B")));
        list.findPrecise(input)
            .setCraftable(false);
        verify(suite, "badge_pattern_removed", !lookup(list, input));
        list.resetStatus();
        verify(suite, "badge_network_cleared", !lookup(list, fluid) && !lookup(list, tagged));
        verify(
            suite,
            "badge_other_network",
            !lookup(
                AEApi.instance()
                    .storage()
                    .createAEStackList(),
                tagged));
    }

    private static boolean lookup(IItemList<IAEStack<?>> list, IAEStack<?> input) {
        return CraftableInputs.isCraftable(true, list, input);
    }

    private static IAEStack<?> taggedItem(String value) {
        ItemStack item = new ItemStack(Items.paper);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("badge_identity", value);
        item.setTagCompound(tag);
        return AEItemStack.create(item);
    }

    private static IAEStack<?> taggedFluid(String value) {
        FluidStack fluid = new FluidStack(FluidRegistry.WATER, 1000);
        fluid.tag = new NBTTagCompound();
        fluid.tag.setString("badge_identity", value);
        return AEFluidStack.create(fluid);
    }

    private static void verify(OptimizerPackTest suite, String name, boolean ok) {
        OptimizerPackTest.check(ok, name);
        suite.row(name, true);
    }
}
