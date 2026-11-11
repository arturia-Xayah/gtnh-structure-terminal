package dev.aestoragevault.packtest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

import cpw.mods.fml.common.gameevent.TickEvent;
import dev.structureterminal.StructureTerminal;
import dev.structureterminal.TerminalSettings;
import dev.structureterminal.bridge.AdvancedTerminalPlan;
import dev.structureterminal.bridge.NativeBridge;
import dev.structureterminal.bridge.StructureScanner;
import dev.structureterminal.server.BuildService;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.blocks.BlockCasings5;

/** Exercises the actual advanced action packet and native EBF elements in a dedicated GTNH world. */
final class AdvancedTerminalChecks {

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void run(WorldServer world, FakePlayer player, String mode) throws Exception {
        run(world, player, mode, null);
    }

    static void run(WorldServer world, FakePlayer player, String mode,
        appeng.api.storage.IMEMonitor<appeng.api.storage.data.IAEItemStack> monitor) throws Exception {
        int x = 24, y = 155, z = 24;
        boolean ae = mode.startsWith("dismantle_ae");
        boolean hatchTest = mode.contains("hatch");
        boolean dismantling = mode.startsWith("dismantle");
        boolean oldDemolish = dev.structureterminal.TerminalConfig.demolish;
        BreakGuard guard = new BreakGuard();
        if (mode.equals("dismantle_protected")) net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(guard);
        if (mode.equals("dismantle_disabled")) dev.structureterminal.TerminalConfig.demolish = false;
        ItemStack[] inventory = player.inventory.mainInventory.clone();
        boolean creative = player.capabilities.isCreativeMode;
        int slot = player.inventory.currentItem;
        double px = player.posX, py = player.posY, pz = player.posZ;
        try {
            for (int dx = -5; dx <= 5; dx++) for (int dy = -1; dy <= 7; dy++)
                for (int dz = -5; dz <= 5; dz++) world.setBlockToAir(x + dx, y + dy, z + dz);
            for (int i = 0; i < inventory.length; i++) player.inventory.mainInventory[i] = null;
            player.inventory.currentItem = 0;
            player.inventory.mainInventory[0] = new ItemStack(StructureTerminal.item);
            player.setPosition(x + .5, y, z - 2);
            world.setBlock(x, y, z, GregTechAPI.sBlockMachines, 0, 3);
            IGregTechTileEntity base = (IGregTechTileEntity) world.getTileEntity(x, y, z);
            base.setInitialValuesAsNBT(
                null,
                (short) ItemList.Machine_Multi_BlastFurnace.get(1)
                    .getItemDamage());
            base.setOwnerUuid(player.getUniqueID());
            base.setOwnerName(player.getCommandSenderName());
            base.setFrontFacing(net.minecraftforge.common.util.ForgeDirection.NORTH);
            NativeBridge bridge = NativeBridge.resolve(world, x, y, z, 2);
            check(bridge != null, "EBF bridge");
            TerminalSettings settings = new TerminalSettings();
            settings.coilTier = 3;
            settings.network = ae;
            settings.keepHatches = !hatchTest || mode.endsWith("keep");
            settings.repeatCount = 9;
            settings.channels.put("glass", 2);
            AdvancedTerminalPlan plan = AdvancedTerminalPlan.create(bridge, player, settings);
            check(
                Integer.valueOf(2)
                    .equals(plan.settings.channels.get("glass")),
                "native glass selection lost");
            if (mode.equals("creative")) {
                materialChoices(world, player);
                channelChoices(bridge, world, player);
                java.util.List<dev.structureterminal.client.TerminalMaterials.Entry> variants = dev.structureterminal.client.TerminalMaterials
                    .variants(3);
                dev.structureterminal.client.TerminalMaterials.Entry variant = variants.get(variants.size() - 1);
                TerminalSettings autoGlass = TerminalSettings.read(settings.write());
                autoGlass.channels.remove("glass");
                autoGlass.glassTier = 3;
                player.inventory.mainInventory[1] = variant.icon.copy();
                AdvancedTerminalPlan autoPlan = AdvancedTerminalPlan.create(bridge, player, autoGlass);
                check(
                    Integer.valueOf(variant.value)
                        .equals(autoPlan.settings.channels.get("glass")),
                    "per-tier Auto ignored supplied glass variant");
                check(player.inventory.mainInventory[1].stackSize == 1, "Auto scan consumed glass");
                player.inventory.mainInventory[1] = null;
            }
            List<StructureScanner.Cell> coils = new ArrayList<StructureScanner.Cell>();
            for (StructureScanner.Cell cell : plan.scan.cells) if (cell.heatingCoil) coils.add(cell);
            check(coils.size() == 16, "EBF heating coil count=" + coils.size());
            int wanted = BlockCasings5.getMetaFromCoilHeat(HeatingCoilLevel.getFromTier((byte) 2));
            int old = BlockCasings5.getMetaFromCoilHeat(HeatingCoilLevel.getFromTier((byte) 0));
            player.capabilities.isCreativeMode = mode.equals("creative") || mode.equals("formed");
            if (mode.equals("supply"))
                player.inventory.mainInventory[1] = new ItemStack(GregTechAPI.sBlockCasings5, 16, wanted);
            if (mode.equals("replace") || mode.equals("replace_all")
                || mode.equals("full")
                || mode.equals("formed")
                || dismantling) {
                for (StructureScanner.Cell c : coils) world.setBlock(c.x, c.y, c.z, GregTechAPI.sBlockCasings5, old, 3);
                ((gregtech.api.metatileentity.implementations.MTEMultiBlockBase) bridge.context).mMachine = true;
                settings.replaceCoils = mode.equals("replace") || mode.equals("full");
                if (mode.equals("replace_all")) settings.operation = TerminalSettings.REPLACE;
                if (dismantling) settings.operation = TerminalSettings.DISMANTLE;
                if (settings.replaceCoils || mode.equals("replace_all"))
                    player.inventory.mainInventory[1] = new ItemStack(GregTechAPI.sBlockCasings5, 5, wanted);
            }
            if (mode.equals("full")) for (int i = 2; i < player.inventory.mainInventory.length; i++)
                player.inventory.mainInventory[i] = new ItemStack(net.minecraft.init.Blocks.stone, 64);
            if (mode.equals("dismantle_full")) for (int i = 1; i < player.inventory.mainInventory.length; i++)
                player.inventory.mainInventory[i] = new ItemStack(net.minecraft.init.Blocks.stone, 64);
            StructureScanner.Cell obstruction = null;
            for (StructureScanner.Cell c : plan.scan.cells)
                if (!c.heatingCoil && !c.air && (c.x != x || c.y != y || c.z != z)) {
                    obstruction = c;
                    break;
                }
            if (obstruction != null) world
                .setBlock(obstruction.x, obstruction.y, obstruction.z, net.minecraft.init.Blocks.diamond_block, 0, 3);
            StructureScanner.Cell protectedCell = null;
            if (dismantling || mode.equals("replace_all")) {
                for (StructureScanner.Cell c : plan.scan.cells)
                    if (c != obstruction && !c.heatingCoil && !c.air && (c.x != x || c.y != y || c.z != z)) {
                        protectedCell = c;
                        break;
                    }
                check(protectedCell != null, "no container protection fixture position");
                world
                    .setBlock(protectedCell.x, protectedCell.y, protectedCell.z, net.minecraft.init.Blocks.chest, 0, 3);
                ((net.minecraft.inventory.IInventory) world
                    .getTileEntity(protectedCell.x, protectedCell.y, protectedCell.z))
                        .setInventorySlotContents(0, new ItemStack(net.minecraft.init.Items.diamond, 3));
            }
            ItemStack hatchDrop = null;
            if (hatchTest) {
                check(protectedCell != null, "no hatch position");
                ((net.minecraft.inventory.IInventory) world
                    .getTileEntity(protectedCell.x, protectedCell.y, protectedCell.z))
                        .setInventorySlotContents(0, null);
                world.setBlockToAir(protectedCell.x, protectedCell.y, protectedCell.z);
                world.setBlock(protectedCell.x, protectedCell.y, protectedCell.z, GregTechAPI.sBlockMachines, 0, 3);
                IGregTechTileEntity hatch = (IGregTechTileEntity) world
                    .getTileEntity(protectedCell.x, protectedCell.y, protectedCell.z);
                hatchDrop = (mode.endsWith("fluid") ? ItemList.Hatch_Input_LV : ItemList.Hatch_Input_Bus_LV).get(1);
                hatch.setInitialValuesAsNBT(null, (short) hatchDrop.getItemDamage());
                hatch.setOwnerUuid(player.getUniqueID());
                hatch.setOwnerName(player.getCommandSenderName());
                hatch.setColorization((byte) 4);
                if (mode.endsWith("filled"))
                    hatch.setInventorySlotContents(0, new ItemStack(net.minecraft.init.Items.diamond, 3));
                if (mode.endsWith("fluid")) ((gregtech.api.metatileentity.implementations.MTEHatchInput) hatch
                    .getMetaTileEntity()).mFluid = new net.minecraftforge.fluids.FluidStack(
                        net.minecraftforge.fluids.FluidRegistry.WATER,
                        1000);
            }
            java.util.Set<net.minecraft.entity.Entity> existingDrops = new java.util.HashSet<>(world.loadedEntityList);
            NBTTagCompound request = new NBTTagCompound();
            request.setInteger("action", 6);
            request.setInteger("x", x);
            request.setInteger("y", y);
            request.setInteger("z", z);
            request.setInteger("side", 2);
            request.setTag("settings", settings.write());
            java.lang.reflect.Field rate = BuildService.class.getDeclaredField("lastRequest");
            rate.setAccessible(true);
            ((java.util.Map<?, ?>) rate.get(BuildService.INSTANCE)).clear();
            long tick = world.getTotalWorldTime();
            if (ae) {
                try {
                    dev.structureterminal.server.AeReturnChecks.build(bridge, player, settings, monitor, mode);
                    check(!mode.endsWith("unlinked"), "unlinked route allowed");
                } catch (IllegalArgumentException e) {
                    if (!mode.endsWith("unlinked")) throw e;
                }
            } else {
                BuildService.INSTANCE.enqueue(player, request);
                BuildService.INSTANCE.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
            }
            check(tick == world.getTotalWorldTime(), "build advanced a world tick");
            int count = 0;
            for (StructureScanner.Cell c : coils) if (world.getBlock(c.x, c.y, c.z) == GregTechAPI.sBlockCasings5
                && world.getBlockMetadata(c.x, c.y, c.z) == wanted) count++;
            check(
                count == (mode.equals("replace") || mode.equals("replace_all") ? 5
                    : (mode.equals("formed") || mode.equals("full") || dismantling) ? 0 : 16),
                "selected coils=" + count + " mode=" + mode);
            if (obstruction != null) check(
                world.getBlock(obstruction.x, obstruction.y, obstruction.z) == net.minecraft.init.Blocks.diamond_block,
                "occupied block changed");
            if (mode.equals("supply")) check(countItems(player, wanted) == 0, "supplied coils not consumed exactly");
            if (mode.equals("replace") || mode.equals("replace_all")) {
                check(countItems(player, wanted) == 0, "replacement material accounting");
                check(countItems(player, old) == 5, "old coils not returned exactly");
            }
            if (mode.equals("full")) {
                check(countItems(player, wanted) == 5, "full inventory consumed replacement coils");
                check(countItems(player, old) == 0, "full inventory received unexpected old coils");
            }
            if (dismantling) {
                int oldCount = 0;
                for (StructureScanner.Cell c : coils) if (world.getBlock(c.x, c.y, c.z) == GregTechAPI.sBlockCasings5
                    && world.getBlockMetadata(c.x, c.y, c.z) == old) oldCount++;
                boolean removed = mode.equals("dismantle") || hatchTest || mode.equals("dismantle_ae");
                check(oldCount == (removed ? 0 : 16), "dismantle remaining coils=" + oldCount);
                check(countItems(player, old) == (removed && !ae ? 16 : 0), "dismantle returns exact coil count");
                if (ae && removed) {
                    appeng.api.storage.data.IAEItemStack recovered = monitor.extractItems(
                        appeng.util.item.AEItemStack.create(new ItemStack(GregTechAPI.sBlockCasings5, 16, old)),
                        appeng.api.config.Actionable.MODULATE,
                        new appeng.api.networking.security.PlayerSource(player, null));
                    check(recovered != null && recovered.getStackSize() == 16, "AE did not receive 16 coils");
                }
                check(world.getTileEntity(x, y, z) == base, "dismantle removed controller");
            }
            if (mode.equals("creative")) {
                for (StructureScanner.Cell c : plan.scan.cells) {
                    if (!world.isAirBlock(c.x, c.y, c.z) && world.getBlock(c.x, c.y, c.z) == GregTechAPI.sBlockMachines
                        && (c.x != x || c.y != y || c.z != z)) {
                        for (ItemStack candidate : c.candidates)
                            check(AdvancedTerminalPlan.isHatch(candidate), "optional hatch placed in no-hatch mode");
                    }
                }
            }
            if (hatchTest) {
                boolean removeHatch = !mode.endsWith("keep");
                check(
                    world.isAirBlock(protectedCell.x, protectedCell.y, protectedCell.z) == removeHatch,
                    "hatch retention mode=" + mode);
                if (removeHatch) {
                    int recovered = 0;
                    java.util.List<ItemStack> returned = new java.util.ArrayList<>();
                    if (ae) {
                        for (appeng.api.storage.data.IAEItemStack entry : monitor.getStorageList()) {
                            ItemStack item = entry.getItemStack();
                            if (item.isItemEqual(hatchDrop) || item.getItem() == net.minecraft.init.Items.diamond) {
                                returned.add(
                                    monitor
                                        .extractItems(
                                            entry.copy(),
                                            appeng.api.config.Actionable.MODULATE,
                                            new appeng.api.networking.security.PlayerSource(player, null))
                                        .getItemStack());
                            }
                        }
                    } else java.util.Collections.addAll(returned, player.inventory.mainInventory);
                    int diamonds = 0;
                    for (ItemStack item : returned) {
                        if (item == null) continue;
                        check(
                            item.getItem() != dev.structureterminal.packing.PackingItems.PACKAGE,
                            "hatch should use native drop, not a full-state package");
                        if (item.getItem() == net.minecraft.init.Items.diamond) diamonds += item.stackSize;
                        if (item.isItemEqual(hatchDrop)) {
                            recovered += item.stackSize;
                            check(
                                item.hasTagCompound() && item.getTagCompound()
                                    .getByte("mColor") == 5,
                                "native hatch dye lost");
                            check(
                                !item.getTagCompound()
                                    .hasKey("mFluid"),
                                "fluid was specially packed");
                        }
                    }
                    check(recovered == 1, "native hatch return count=" + recovered);
                    check(diamonds == (mode.endsWith("filled") ? 3 : 0), "native contents drop count=" + diamonds);
                    for (Object entity : world.loadedEntityList)
                        if (entity instanceof net.minecraft.entity.item.EntityItem)
                            check(existingDrops.contains(entity), "native drops duplicated on ground");
                }
            } else if (protectedCell != null) {
                check(
                    world.getBlock(protectedCell.x, protectedCell.y, protectedCell.z)
                        == net.minecraft.init.Blocks.chest,
                    "operation removed a container");
                ItemStack kept = ((net.minecraft.inventory.IInventory) world
                    .getTileEntity(protectedCell.x, protectedCell.y, protectedCell.z)).getStackInSlot(0);
                check(
                    kept != null && kept.getItem() == net.minecraft.init.Items.diamond && kept.stackSize == 3,
                    "operation changed container inventory");
            }
            check(StructureScanner.BUILD_CELL.get() == null, "placement context leaked");
        } finally {
            dev.structureterminal.TerminalConfig.demolish = oldDemolish;
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(guard);
            try {
                for (int dx = -5; dx <= 5; dx++) for (int dy = -1; dy <= 7; dy++)
                    for (int dz = -5; dz <= 5; dz++) world.setBlockToAir(x + dx, y + dy, z + dz);
            } finally {
                System.arraycopy(inventory, 0, player.inventory.mainInventory, 0, inventory.length);
                player.inventory.currentItem = slot;
                player.capabilities.isCreativeMode = creative;
                player.setPosition(px, py, pz);
            }
        }
    }

    public static final class BreakGuard {

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void cancel(net.minecraftforge.event.world.BlockEvent.BreakEvent event) {
            event.setCanceled(true);
        }
    }

    private static void channelChoices(NativeBridge bridge, WorldServer world, FakePlayer player) throws Exception {
        java.util.Set<String> seen = new java.util.HashSet<>();
        com.google.gson.JsonObject catalog = new com.google.gson.JsonObject();
        TerminalSettings configured = new TerminalSettings();
        List<String> missing = new ArrayList<>();
        for (String[] page : dev.structureterminal.TerminalChannels.PAGES) for (String channel : page) {
            check(seen.add(channel), "duplicate channel " + channel);
            check(!channel.equals("height") && !channel.contains("hatch"), "excluded channel exposed");
            java.util.List<dev.structureterminal.client.TerminalMaterials.Entry> entries = dev.structureterminal.TerminalChannels
                .entries(channel);
            com.google.gson.JsonArray items = new com.google.gson.JsonArray();
            for (dev.structureterminal.client.TerminalMaterials.Entry e : entries) if (e.icon != null) {
                com.google.gson.JsonObject item = new com.google.gson.JsonObject();
                item.addProperty("value", e.value);
                item.addProperty("name", e.icon.getDisplayName());
                item.addProperty(
                    "registry",
                    String.valueOf(net.minecraft.item.Item.itemRegistry.getNameForObject(e.icon.getItem())));
                item.addProperty("meta", e.icon.getItemDamage());
                items.add(item);
            }
            catalog.add(channel, items);
            if (dev.structureterminal.TerminalChannels.numericMax(channel) == 0
                && !dev.structureterminal.TerminalChannels.toggle(channel))
                if (entries.size() <= 1) missing.add(channel);
            int value = entries.size() > 1 ? entries.get(entries.size() - 1).value : 1;
            configured.channels.put(channel, value);
        }
        java.nio.file.Files.write(
            java.nio.file.Paths.get("terminal-channels.json"),
            new com.google.gson.GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(catalog)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        check(missing.isEmpty(), "missing native indicators: " + missing);
        for (gregtech.common.misc.GTStructureChannels channel : gregtech.common.misc.GTStructureChannels.values())
            if (!channel.get()
                .equals("coil")
                && !channel.get()
                    .equals("glass")
                && !channel.get()
                    .equals("height")
                && !channel.get()
                    .equals("gt_hatch"))
                check(seen.contains(channel.get()), "missing GT channel " + channel.get());
        TerminalSettings restored = TerminalSettings.read(configured.write());
        AdvancedTerminalPlan plan = AdvancedTerminalPlan.create(bridge, player, restored);
        for (java.util.Map.Entry<String, Integer> e : configured.channels.entrySet()) {
            check(
                e.getValue()
                    .equals(plan.settings.channels.get(e.getKey())),
                "plan discarded " + e.getKey());
            for (StructureScanner.Cell cell : plan.scan.cells) check(
                com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor
                    .getChannelData(cell.advancedTrigger, e.getKey()) == e.getValue(),
                "cell trigger lost " + e.getKey());
        }
        int x = 24, y = 185, z = 24;
        world.setBlock(x, y, z, GregTechAPI.sBlockMachines, 0, 3);
        try {
            IGregTechTileEntity base = (IGregTechTileEntity) world.getTileEntity(x, y, z);
            base.setInitialValuesAsNBT(
                null,
                (short) gtPlusPlus.xmod.gregtech.api.enums.GregtechItemList.ChemicalPlant_Controller.get(1)
                    .getItemDamage());
            base.setFrontFacing(net.minecraftforge.common.util.ForgeDirection.NORTH);
            base.setOwnerUuid(player.getUniqueID());
            base.setOwnerName(player.getCommandSenderName());
            NativeBridge plant = NativeBridge.resolve(world, x, y, z, 2);
            java.util.List<dev.structureterminal.client.TerminalMaterials.Entry> pipes = dev.structureterminal.TerminalChannels
                .entries("pipe");
            java.util.Set<String> previousPipes = null;
            for (int value : new int[] { 1, 4 }) {
                TerminalSettings choice = new TerminalSettings();
                choice.channels.put("pipe", value);
                StructureScanner.Scan nativeScan = StructureScanner.scan(plant, player, choice);
                AdvancedTerminalPlan chosen = AdvancedTerminalPlan.create(plant, player, choice);
                java.util.Set<String> selectedPipes = new java.util.HashSet<>();
                for (StructureScanner.Cell cell : chosen.scan.cells) for (ItemStack candidate : cell.candidates)
                    for (dev.structureterminal.client.TerminalMaterials.Entry entry : pipes)
                        if (entry.icon != null && dev.structureterminal.bridge.ItemKeys.same(entry.icon, candidate)) {
                            StructureScanner.Cell nativeCell = nativeScan.byPosition.get(cell.position());
                            String key = dev.structureterminal.bridge.ItemKeys.key(candidate);
                            check(
                                nativeCell != null && nativeCell.keyedCandidates.containsKey(key),
                                "pipe differs from native projector");
                            selectedPipes.add(key);
                        }
                check(!selectedPipes.isEmpty(), "chemical plant pipe candidates absent");
                if (previousPipes != null) check(!previousPipes.equals(selectedPipes), "pipe selector had no effect");
                previousPipes = selectedPipes;
            }
        } finally {
            world.setBlockToAir(x, y, z);
        }
        // An unset channel must fall back to native main-channel semantics, rather than retain an old selection.
        restored.channels.remove("solenoid");
        check(
            !AdvancedTerminalPlan.create(bridge, player, restored).settings.channels.containsKey("solenoid"),
            "Auto did not clear channel");
    }

    private static void materialChoices(WorldServer world, FakePlayer player) {
        java.util.List<dev.structureterminal.client.TerminalMaterials.Entry> coils = dev.structureterminal.client.TerminalMaterials
            .coils();
        check(coils.size() == HeatingCoilLevel.getMaxTier() + 2, "missing coil choices");
        for (int i = 1; i < coils.size(); i++) {
            check(coils.get(i).value == i, "coil order");
            check(AdvancedTerminalPlan.coilTier(coils.get(i).icon) == i, "coil icon/tier mismatch");
        }
        java.util.Set<Integer> seen = new java.util.HashSet<Integer>();
        int previous = -1;
        java.util.List<dev.structureterminal.client.TerminalMaterials.Entry> glass = dev.structureterminal.client.TerminalMaterials
            .glasses();
        java.util.Set<Integer> tiers = new java.util.HashSet<>();
        for (dev.structureterminal.client.TerminalMaterials.Entry e : dev.structureterminal.client.TerminalMaterials
            .glassTiers()) {
            if (e.value == 0) continue;
            check(tiers.add(e.tier), "duplicate tier row");
            java.util.List<dev.structureterminal.client.TerminalMaterials.Entry> variants = dev.structureterminal.client.TerminalMaterials
                .variants(e.tier);
            check(variants.get(0).value == 0 && variants.size() > 1, "missing per-tier Auto");
            for (dev.structureterminal.client.TerminalMaterials.Entry variant : variants)
                check(variant.tier == e.tier, "variant tier mismatch");
        }
        check(glass.size() > 11 && glass.get(0).value == 0, "glass catalog/auto missing");
        com.gtnewhorizon.structurelib.structure.IStructureElement<Object> element = gregtech.api.util.GTStructureUtility
            .chainAllGlasses(-1, (o, tier) -> {}, o -> -1);
        for (dev.structureterminal.client.TerminalMaterials.Entry entry : glass) {
            check(seen.add(entry.value), "duplicate glass channel");
            check(entry.tier >= previous, "glass not sorted by tier");
            previous = entry.tier;
            if (entry.value == 0) continue;
            ItemStack trigger = new ItemStack(StructureTerminal.item);
            com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor
                .setChannelData(trigger, "glass", entry.value);
            com.gtnewhorizon.structurelib.structure.IStructureElement.BlocksToPlace blocks = element.getBlocksToPlace(
                new Object(),
                world,
                0,
                150,
                0,
                trigger,
                com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment
                    .fromLegacy((p, simulate, count) -> java.util.Collections.emptyMap(), player, chat -> {}));
            check(
                blocks != null && blocks.getPredicate()
                    .test(entry.icon),
                "glass icon/native selection mismatch " + entry.value);
        }
    }

    private static int countItems(FakePlayer player, int meta) {
        int n = 0;
        for (ItemStack stack : player.inventory.mainInventory)
            if (stack != null && stack.getItem() == net.minecraft.item.Item.getItemFromBlock(GregTechAPI.sBlockCasings5)
                && stack.getItemDamage() == meta) n += stack.stackSize;
        return n;
    }
}
