package dev.aeoptimizer.packtest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.config.Actionable;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.parts.IPatternTerminal;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import dev.aeoptimizer.network.TileOptimizer;
import dev.aeoptimizer.pattern.AssemblyLineRenamer;
import dev.aeoptimizer.pattern.RenamePatternEncoder;
import dev.aeoptimizer.ui.OptimizerContainer;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;

final class RenameEncodingPackChecks {

    static void run(OptimizerPackTest suite) throws Exception {
        ItemStack[] backup = suite.player.inventory.mainInventory.clone();
        List<ICraftingPatternDetails> previous = new ArrayList<>(suite.provider.patterns);
        try {
            Arrays.fill(suite.player.inventory.mainInventory, null);
            OptimizerContainer gui = new OptimizerContainer(suite.optimizer);
            check(suite, "rename_encode_default_off", !suite.optimizer.autoEncodeRename);
            check(
                suite,
                "rename_encode_gui_toggle",
                gui.enchantItem(suite.player, 2) && suite.optimizer.autoEncodeRename);
            NBTTagCompound nbt = new NBTTagCompound();
            suite.optimizer.writeToNBT(nbt);
            TileOptimizer restored = new TileOptimizer();
            restored.readFromNBT(nbt);
            check(suite, "rename_encode_nbt", restored.autoEncodeRename);
            gui.updateProgressBar(3, 1);
            check(suite, "rename_encode_gui_sync", gui.autoEncodeRename);
            suite.player.setPosition(100, 81, 100);
            check(
                suite,
                "rename_encode_gui_distance",
                !gui.enchantItem(suite.player, 2) && suite.optimizer.autoEncodeRename);
            suite.player.setPosition(8, 81, 8);

            // A failed import must preserve the previous matrix AND its crafting/processing mode.
            boolean previousMode = AssemblyLineRenamePackChecks.terminal.isCraftingRecipe();
            ItemStack sentinel = new ItemStack(Items.diamond);
            AssemblyLineRenamePackChecks.terminal.getAEInventoryByName(StorageName.CRAFTING_INPUT)
                .putAEStackInSlot(1, AEItemStack.create(sentinel));
            AssemblyLineRenamePackChecks.importRecipe(suite, AssemblyLineRenamer.RECIPE_ID);
            check(
                suite,
                "rename_encode_missing_blanks_atomic",
                AssemblyLineRenamePackChecks.terminal.isCraftingRecipe() == previousMode
                    && AssemblyLineRenamePackChecks.terminal.getAEInventoryByName(StorageName.CRAFTING_INPUT)
                        .getAEStackInSlot(1)
                        .equals(AEItemStack.create(sentinel))
                    && patterns(suite).isEmpty());

            suite.player.inventory.mainInventory[0] = blank(2);
            ItemStack unrelated = new ItemStack(Items.stick);
            suite.player.inventory.mainInventory[35] = unrelated;
            AssemblyLineRenamePackChecks.importRecipe(suite, AssemblyLineRenamer.RECIPE_ID);
            List<ItemStack> made = patterns(suite);
            check(suite, "rename_encode_import_creates_two", made.size() == 2 && blanks(suite) == 0);
            check(
                suite,
                "rename_encode_preserves_unrelated_stack_identity",
                suite.player.inventory.mainInventory[35] == unrelated);
            ICraftingPatternDetails p1 = details(made.get(0), suite), p2 = details(made.get(1), suite);
            check(
                suite,
                "rename_encode_exact_unit_patterns",
                RenamePatternEncoder.matches(p1, foil(), renamed(1))
                    && RenamePatternEncoder.matches(p2, foil(), renamed(2)));
            check(suite, "rename_encode_no_substitution", !p1.canSubstitute() && !p1.canBeSubstitute());
            check(suite, "rename_encode_no_mold_input", p1.getCondensedAEInputs().length == 1);
            check(suite, "rename_encode_native_processing_item", !p1.isCraftable());
            check(
                suite,
                "rename_encode_output_identity",
                p1.getCondensedAEOutputs()[0].equals(
                    AssemblyLineRenamePackChecks.terminal.getAEInventoryByName(StorageName.CRAFTING_INPUT)
                        .getAEStackInSlot(1)));

            suite.player.inventory.mainInventory[2] = blank(4);
            AssemblyLineRenamePackChecks.importRecipe(suite, AssemblyLineRenamer.RECIPE_ID);
            check(suite, "rename_encode_backpack_dedup", patterns(suite).size() == 2 && blanks(suite) == 4);

            Arrays.fill(suite.player.inventory.mainInventory, new ItemStack(net.minecraft.init.Blocks.cobblestone, 64));
            suite.player.inventory.mainInventory[0] = blank(4);
            AssemblyLineRenamePackChecks.terminal.getAEInventoryByName(StorageName.CRAFTING_INPUT)
                .putAEStackInSlot(1, AEItemStack.create(sentinel));
            AssemblyLineRenamePackChecks.importRecipe(suite, AssemblyLineRenamer.RECIPE_ID);
            check(
                suite,
                "rename_encode_full_backpack_atomic",
                blanks(suite) == 4 && patterns(suite).isEmpty()
                    && AssemblyLineRenamePackChecks.terminal.getAEInventoryByName(StorageName.CRAFTING_INPUT)
                        .getAEStackInSlot(1)
                        .equals(AEItemStack.create(sentinel)));

            // An advertised recipe with the same output but different input must not suppress generation.
            Arrays.fill(suite.player.inventory.mainInventory, null);
            suite.player.inventory.mainInventory[0] = blank(2);
            suite.setPatterns(suite.pattern(new ItemStack[] { new ItemStack(Items.iron_ingot) }, renamed(1)));
            AssemblyLineRenamePackChecks.importRecipe(suite, AssemblyLineRenamer.RECIPE_ID);
            check(suite, "rename_encode_wrong_route_not_deduped", patterns(suite).size() == 2 && blanks(suite) == 0);
            suite.setPatterns(p1, p2);
            Arrays.fill(suite.player.inventory.mainInventory, null);
            AssemblyLineRenamePackChecks.importRecipe(suite, AssemblyLineRenamer.RECIPE_ID);
            check(
                suite,
                "rename_encode_network_dedup_without_blanks",
                patterns(suite).isEmpty() && !AssemblyLineRenamePackChecks.terminal.isCraftingRecipe());
            suite.setPatterns(previous.toArray(new ICraftingPatternDetails[0]));

            // Source order: terminal blanks, inventory blanks, then powered ME extraction.
            AssemblyLineRenamePackChecks.terminal.getInventoryByName("pattern")
                .setInventorySlotContents(0, blank(2));
            AssemblyLineRenamePackChecks.importRecipe(suite, AssemblyLineRenamer.RECIPE_ID);
            check(
                suite,
                "rename_encode_terminal_blank_source",
                patterns(suite).size() == 2 && AssemblyLineRenamePackChecks.terminal.getInventoryByName("pattern")
                    .getStackInSlot(0) == null);
            Arrays.fill(suite.player.inventory.mainInventory, null);
            IAEItemStack blankKey = IPatternTerminal.createBlankPattern();
            check(
                suite,
                "rename_encode_network_blank_stock",
                suite.storage.injectItems(
                    blankKey.copy()
                        .setStackSize(2),
                    Actionable.MODULATE,
                    suite.source) == null);
            AssemblyLineRenamePackChecks.importRecipe(suite, AssemblyLineRenamer.RECIPE_ID);
            IAEItemStack remains = suite.storage.extractItems(
                blankKey.copy()
                    .setStackSize(2),
                Actionable.SIMULATE,
                suite.source);
            check(
                suite,
                "rename_encode_network_blank_source",
                patterns(suite).size() == 2 && (remains == null || remains.getStackSize() == 0));

            // Network contains only one blank: simulation must not consume it or produce half a set.
            Arrays.fill(suite.player.inventory.mainInventory, null);
            suite.storage.injectItems(
                blankKey.copy()
                    .setStackSize(1),
                Actionable.MODULATE,
                suite.source);
            AssemblyLineRenamePackChecks.importRecipe(suite, AssemblyLineRenamer.RECIPE_ID);
            remains = suite.storage.extractItems(
                blankKey.copy()
                    .setStackSize(2),
                Actionable.MODULATE,
                suite.source);
            check(
                suite,
                "rename_encode_network_shortage_no_partial",
                patterns(suite).isEmpty() && remains != null && remains.getStackSize() == 1);
            partialExtraction(suite);
        } finally {
            System.arraycopy(backup, 0, suite.player.inventory.mainInventory, 0, backup.length);
            suite.setPatterns(previous.toArray(new ICraftingPatternDetails[0]));
            suite.optimizer.autoEncodeRename = false;
        }
    }

    private static void partialExtraction(OptimizerPackTest suite) throws Exception {
        appeng.api.storage.IMEMonitor<IAEItemStack> shortMonitor = (appeng.api.storage.IMEMonitor<IAEItemStack>) java.lang.reflect.Proxy
            .newProxyInstance(
                RenameEncodingPackChecks.class.getClassLoader(),
                new Class<?>[] { appeng.api.storage.IMEMonitor.class },
                (proxy, method, args) -> {
                    if (method.getName()
                        .equals("extractItems")) {
                        IAEItemStack request = ((IAEItemStack) args[0]).copy();
                        return args[1] == Actionable.SIMULATE ? request : request.setStackSize(1);
                    }
                    return method.invoke(suite.storage, args);
                });
        appeng.container.implementations.ContainerPatternTerm container = new appeng.container.implementations.ContainerPatternTermEx(
            suite.player.inventory,
            AssemblyLineRenamePackChecks.terminal) {

            @Override
            public appeng.api.storage.IMEMonitor<IAEItemStack> getItemMonitor() {
                return shortMonitor;
            }
        };
        try {
            ItemStack[] inputs = { foil(), foil(), foil() };
            boolean success = RenamePatternEncoder.generate(container, inputs, AssemblyLineRenamer.rename(inputs));
            ItemStack refund = AssemblyLineRenamePackChecks.terminal.getInventoryByName("pattern")
                .getStackInSlot(0);
            check(
                suite,
                "rename_encode_partial_extraction_refunded",
                !success && patterns(suite).isEmpty()
                    && IPatternTerminal.isBlankPattern(refund)
                    && refund.stackSize == 1);
            AssemblyLineRenamePackChecks.terminal.getInventoryByName("pattern")
                .setInventorySlotContents(0, null);
        } finally {
            container.onContainerClosed(suite.player);
        }
    }

    private static ItemStack blank(int n) {
        ItemStack item = IPatternTerminal.createBlankPattern()
            .getItemStack();
        item.stackSize = n;
        return item;
    }

    private static ItemStack foil() {
        return GTOreDictUnificator.get(OrePrefixes.foil, Materials.Gallium, 1);
    }

    private static ItemStack renamed(int n) {
        return foil().setStackDisplayName("重命名" + n);
    }

    private static List<ItemStack> patterns(OptimizerPackTest suite) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : suite.player.inventory.mainInventory)
            if (item != null && item.getItem() instanceof ICraftingPatternItem) items.add(item);
        return items;
    }

    private static int blanks(OptimizerPackTest suite) {
        int total = 0;
        for (ItemStack item : suite.player.inventory.mainInventory)
            if (IPatternTerminal.isBlankPattern(item)) total += item.stackSize;
        return total;
    }

    private static ICraftingPatternDetails details(ItemStack item, OptimizerPackTest suite) {
        return ((ICraftingPatternItem) item.getItem()).getPatternForItem(item, suite.world);
    }

    private static void check(OptimizerPackTest suite, String name, boolean success) {
        OptimizerPackTest.check(success, name);
        suite.row(name, true);
    }
}
