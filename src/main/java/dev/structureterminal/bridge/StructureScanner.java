package dev.structureterminal.bridge;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.ChannelDescription;
import com.gtnewhorizon.structurelib.StructureEvent.StructureElementVisitedEvent;
import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureWalker;
import com.gtnewhorizon.structurelib.structure.StructureUtility;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dev.structureterminal.TerminalConfig;
import dev.structureterminal.TerminalSettings;

public final class StructureScanner {

    private static final ThreadLocal<Collector> SCAN = new ThreadLocal<Collector>();
    public static final ThreadLocal<Cell> BUILD_CELL = new ThreadLocal<Cell>();
    public static final ThreadLocal<Map<String, Cell>> BUILD_LOOKUP = new ThreadLocal<Map<String, Cell>>();

    /** Called by the narrowly scoped StructureLibAPI transformer during preview collection. */
    public static boolean suppressHints() {
        return SCAN.get() != null;
    }

    /** Read-only StructureLib traversal on either side; never calls spawnHint, check or placeBlock. */
    public static <T> boolean collectDefinition(Object context, ItemStack trigger, IStructureElement<T>[] elements,
        World world, ExtendedFacing facing, int x, int y, int z, int a, int b, int c, boolean hintsOnly,
        Boolean check) {
        if (!suppressHints() || !hintsOnly || check != null)
            throw new IllegalStateException("Not a terminal collection");
        IStructureDefinition<T> traversal = new IStructureDefinition<T>() {

            @Override
            public IStructureElement<T>[] getStructureFor(String name) {
                return elements;
            }

            @Override
            public boolean isContainedInStructure(String name, int pa, int pb, int pc) {
                throw new UnsupportedOperationException();
            }
        };
        traversal.iterate("terminal-read-only", world, facing, x, y, z, a, b, c, new IStructureWalker<T>() {

            @Override
            public boolean visit(IStructureElement<T> element, World w, int px, int py, int pz, int pa, int pb,
                int pc) {
                return true;
            }

            @Override
            public boolean blockNotLoaded(IStructureElement<T> element, World w, int px, int py, int pz, int pa, int pb,
                int pc) {
                throw new IllegalArgumentException("Structure contains unloaded chunks");
            }
        });
        return true;
    }

    public static final class Cell {

        public int x, y, z;
        public IStructureElement<Object> element;
        public List<ItemStack> candidates = new ArrayList<ItemStack>();
        public Map<String, ItemStack> keyedCandidates = new LinkedHashMap<String, ItemStack>();
        public String group;
        public String selectionKey;
        public String description = "";
        public boolean candidateError;
        public boolean air;

        public ItemStack selected(TerminalSettings settings) {
            String wanted = settings.choices.get(selectionKey);
            if (wanted == null) wanted = settings.choices.get(group);
            return keyedCandidates.get(wanted);
        }

        public String position() {
            return pos(x, y, z);
        }
    }

    public static final class Scan {

        public Cell controller;

        public final List<Cell> cells = new ArrayList<Cell>();
        public final Map<String, List<ItemStack>> channels = new TreeMap<String, List<ItemStack>>();
        public final Map<String, List<Integer>> channelValues = new TreeMap<String, List<Integer>>();
        public final Map<String, Cell> groups = new LinkedHashMap<String, Cell>();
        public final Map<String, Cell> byPosition = new LinkedHashMap<String, Cell>();
        public final Map<String, Integer> quantities = new LinkedHashMap<String, Integer>();
        public int unknown;
        public int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
    }

    private static final class Collector {

        NativeBridge bridge;
        EntityPlayer player;
        TerminalSettings settings;
        Scan result = new Scan();
        long deadline = System.nanoTime() + TerminalConfig.scanMillis * 1000000L;
        Set<String> discovered = new LinkedHashSet<String>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        List<ItemStack> candidatePool;
    }

    /** Error deliberately bypasses StructureLib's per-listener Exception catch; it is caught at this scan boundary. */
    private static final class ScanLimit extends Error {

        private static final long serialVersionUID = 1L;
    }

    public static Scan scan(NativeBridge bridge, EntityPlayer player, TerminalSettings settings) {
        if (StructureLibAPI.isInstrumentEnabled())
            throw new IllegalStateException("Another structure tool is scanning");
        Collector collector = new Collector();
        collector.bridge = bridge;
        collector.player = player;
        collector.settings = settings;
        if (!bridge.canMirror(settings.mirror)) throw new IllegalArgumentException("Unsupported mirror mode");
        SCAN.set(collector);
        StructureLibAPI.enableInstrument(collector);
        try {
            bridge.constructable.construct(settings.trigger(), true);
        } catch (ScanLimit e) {
            throw new IllegalArgumentException("Structure scan exceeded the configured cell or time limit");
        } finally {
            StructureLibAPI.disableInstrument();
            SCAN.remove();
        }
        if (collector.result.cells.isEmpty())
            throw new IllegalArgumentException("Controller does not expose a traversable StructureLib definition");
        for (String name : collector.discovered) addChannel(collector.result, name);
        for (String name : settings.channels.keySet()) addChannel(collector.result, name);
        Cell controller = new Cell();
        controller.x = bridge.tile.xCoord;
        controller.y = bridge.tile.yCoord;
        controller.z = bridge.tile.zCoord;
        controller.group = ItemKeys.digest("controller");
        controller.selectionKey = controller.group;
        try {
            Object stack = bridge.context.getClass()
                .getMethod("getStackForm", long.class)
                .invoke(bridge.context, 1L);
            if (stack instanceof ItemStack) controller.candidates.add((ItemStack) stack);
        } catch (ReflectiveOperationException ignored) {}
        if (controller.candidates.isEmpty()) {
            Block block = bridge.tile.getWorldObj()
                .getBlock(controller.x, controller.y, controller.z);
            if (Item.getItemFromBlock(block) != null) controller.candidates.add(
                new ItemStack(
                    block,
                    1,
                    block.getDamageValue(bridge.tile.getWorldObj(), controller.x, controller.y, controller.z)));
        }
        collector.result.controller = controller;
        collector.result.minY = Math.min(collector.result.minY, controller.y);
        collector.result.maxY = Math.max(collector.result.maxY, controller.y);
        return collector.result;
    }

    private static void addChannel(Scan scan, String name) {
        List<ItemStack> stacks = new ArrayList<ItemStack>();
        List<Integer> values = new ArrayList<Integer>();
        ChannelDescription description = ChannelDescription.getAll()
            .get(name);
        if (description != null) {
            List<Map.Entry<ItemStack, Integer>> entries = new ArrayList<Map.Entry<ItemStack, Integer>>(
                description.getItems()
                    .entrySet());
            Collections.sort(entries, (a, b) -> Integer.compare(a.getValue(), b.getValue()));
            for (Map.Entry<ItemStack, Integer> e : entries) {
                stacks.add(
                    e.getKey()
                        .copy());
                values.add(e.getValue());
            }
        }
        scan.channels.put(name, stacks);
        scan.channelValues.put(name, values);
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public void visited(StructureElementVisitedEvent event) {
        Collector c = SCAN.get();
        if (c == null) {
            Map<String, Cell> lookup = BUILD_LOOKUP.get();
            if (lookup != null) BUILD_CELL.set(lookup.get(pos(event.getX(), event.getY(), event.getZ())));
            return;
        }
        if (event.getInstrumentIdentifier() != c) return;
        if (c.result.cells.size() >= TerminalConfig.cells || System.nanoTime() > c.deadline) throw new ScanLimit();
        Cell cell = new Cell();
        int[] position = c.bridge.previewPosition(event.getX(), event.getY(), event.getZ(), c.settings.mirror);
        cell.x = position[0];
        cell.y = position[1];
        cell.z = position[2];
        cell.element = (IStructureElement<Object>) event.getElement();
        cell.air = cell.element == StructureUtility.<Object>isAir();
        if (c.result.byPosition.containsKey(cell.position())) return;
        discoverChannels(cell.element, c, 0);
        try {
            IItemSource empty = (p, s, count) -> Collections.emptyMap();
            AutoPlaceEnvironment env = AutoPlaceEnvironment.fromLegacy(empty, c.player, chat -> {});
            IStructureElement.BlocksToPlace blocks = cell.element.getBlocksToPlace(
                c.bridge.context,
                event.getWorld(),
                cell.x,
                cell.y,
                cell.z,
                c.settings.trigger(),
                env);
            if (c.candidatePool == null) {
                Map<String, ItemStack> pool = new LinkedHashMap<String, ItemStack>();
                for (ChannelDescription d : ChannelDescription.getAll()
                    .values())
                    for (ItemStack s : d.getItems()
                        .keySet()) pool.put(ItemKeys.key(s), s);
                for (ItemStack s : c.player.inventory.mainInventory) if (s != null) pool.put(ItemKeys.key(s), s);
                for (ItemStack s : GregTechCatalog.items()) pool.put(ItemKeys.key(s), s);
                c.candidatePool = new ArrayList<ItemStack>(pool.values());
            }
            CandidateDiscovery.Result candidates = CandidateDiscovery
                .resolve(cell.element, blocks, c.candidatePool, c.deadline);
            cell.candidates.addAll(candidates.items);
            cell.candidateError = candidates.incomplete;
            List<String> descriptions = cell.element.getDescription(c.bridge.context);
            if (descriptions != null) cell.description = String.join(", ", descriptions);
        } catch (RuntimeException e) {
            cell.candidateError = true;
        }
        if (cell.candidates.isEmpty()) {
            cell.group = ItemKeys.digest(
                "unknown:" + cell.element.getClass()
                    .getName() + ":" + cell.description);
            if (!cell.air) c.result.unknown++;
        } else cell.group = ItemKeys.group(cell.candidates);
        Cell canonical = c.result.groups.get(cell.group);
        if (canonical != null) {
            cell.candidates = canonical.candidates;
            cell.keyedCandidates = canonical.keyedCandidates;
        } else for (ItemStack candidate : cell.candidates) cell.keyedCandidates.put(ItemKeys.key(candidate), candidate);
        cell.selectionKey = ItemKeys.digest(
            cell.group + "@"
                + (cell.x - c.bridge.tile.xCoord)
                + ","
                + (cell.y - c.bridge.tile.yCoord)
                + ","
                + (cell.z - c.bridge.tile.zCoord));
        c.result.cells.add(cell);
        c.result.byPosition.put(cell.position(), cell);
        if (!cell.air) c.result.groups.putIfAbsent(cell.group, cell);
        Integer n = c.result.quantities.get(cell.group);
        c.result.quantities.put(cell.group, n == null ? 1 : n + 1);
        c.result.minY = Math.min(c.result.minY, cell.y);
        c.result.maxY = Math.max(c.result.maxY, cell.y);
    }

    /**
     * 1.4.45 has no channel enumeration API. Inspect only structure-element wrappers, never controller/world graphs.
     */
    private static void discoverChannels(Object element, Collector c, int depth) {
        if (element == null || depth > 24 || c.seen.size() > 10000 || !c.seen.add(element)) return;
        if (element instanceof String) {
            String s = (String) element;
            if (ChannelDescription.has(s)) c.discovered.add(s);
            return;
        }
        if (element.getClass()
            .isArray()) {
            for (int i = 0; i < Array.getLength(element); i++) discoverChannels(Array.get(element, i), c, depth + 1);
        } else if (element instanceof IStructureElement) {
            for (Class<?> type = element.getClass(); type != null
                && type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.getType()
                        .isPrimitive()) continue;
                    try {
                        field.setAccessible(true);
                        Object value = field.get(element);
                        if (value instanceof IStructureElement || value instanceof String
                            || (value != null && value.getClass()
                                .isArray()))
                            discoverChannels(value, c, depth + 1);
                    } catch (ReflectiveOperationException | RuntimeException ignored) {}
                }
            }
        }
    }

    public static String pos(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}
