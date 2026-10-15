package dev.structureterminal.server;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.structureterminal.StructureTerminal;
import dev.structureterminal.TerminalConfig;
import dev.structureterminal.TerminalSettings;
import dev.structureterminal.bridge.NativeBridge;
import dev.structureterminal.bridge.StructureScanner;
import dev.structureterminal.bridge.StructureScanner.Cell;
import dev.structureterminal.bridge.StructureScanner.Scan;
import dev.structureterminal.core.BuildProgress;
import dev.structureterminal.core.Limits;
import dev.structureterminal.net.TerminalNetwork;

public final class BuildService {

    public static final BuildService INSTANCE = new BuildService();
    private final ConcurrentLinkedQueue<Runnable> requests = new ConcurrentLinkedQueue<Runnable>();
    private final AtomicInteger queued = new AtomicInteger();
    private final Map<UUID, Job> jobs = new HashMap<UUID, Job>();
    private final Map<UUID, Long> lastRequest = new HashMap<UUID, Long>();
    private int ticks;

    private static final class Job {

        EntityPlayerMP player;
        NativeBridge bridge;
        ItemStack tool;
        TerminalSettings settings;
        Scan scan;
        int x, y, z, side;
        boolean demolish, replace;
        ExtendedFacing orientation;
        int cursor, removed, skipped;
        BuildProgress progress = new BuildProgress();
    }

    public void enqueue(EntityPlayerMP player, NBTTagCompound tag) {
        if (queued.incrementAndGet() > 128) {
            queued.decrementAndGet();
            return;
        }
        requests.add(() -> {
            queued.decrementAndGet();
            try {
                request(player, tag);
            } catch (RuntimeException e) {
                reply(
                    player,
                    "structureterminal.error",
                    e.getMessage() == null ? e.getClass()
                        .getSimpleName() : e.getMessage());
            }
        });
    }

    private void request(EntityPlayerMP player, NBTTagCompound tag) {
        int action = tag.getInteger("action");
        if (action == 2) {
            jobs.remove(player.getUniqueID());
            reply(player, "structureterminal.stopped", "");
            return;
        }
        long now = System.nanoTime();
        Long old = lastRequest.put(player.getUniqueID(), now);
        if (old != null && now - old < 250000000L) return;
        if (player.isDead || player.playerNetServerHandler == null) return;
        ItemStack held = player.getHeldItem();
        if (held == null || held.getItem() != StructureTerminal.item)
            throw new IllegalArgumentException("Hold the structure terminal");
        int x = tag.getInteger("x"), y = tag.getInteger("y"), z = tag.getInteger("z"), side = tag.getInteger("side");
        if (side < 0 || side > 5
            || !Limits.inReach(player.posX, player.posY, player.posZ, x, y, z, TerminalConfig.reach)) {
            throw new IllegalArgumentException("Controller is out of reach");
        }
        if (!player.capabilities.allowEdit || !player.worldObj.canMineBlock(player, x, y, z))
            throw new IllegalArgumentException("Cannot modify this controller");
        NativeBridge bridge = NativeBridge.resolve(player.worldObj, x, y, z, side);
        if (bridge == null) throw new IllegalArgumentException("Unsupported controller");
        TerminalSettings settings = TerminalSettings.read(tag.getCompoundTag("settings"));
        if (!bridge.canMirror(settings.mirror)) throw new IllegalArgumentException("Unsupported mirror mode");
        if (action == 0) {
            settings.save(held);
            player.inventoryContainer.detectAndSendChanges();
            return;
        }
        if (action != 1 && action != 3 && action != 4) throw new IllegalArgumentException("Unknown terminal action");
        if (jobs.size() >= 4 && !jobs.containsKey(player.getUniqueID()))
            throw new IllegalArgumentException("Four construction jobs are already active; retry after one finishes");
        if (action != 3 && !bridge.supportsSurvivalConstruction())
            throw new IllegalArgumentException("Native survival construction is not implemented by this controller");
        if (action == 3 && (!TerminalConfig.demolish || !tag.getBoolean("confirmed")))
            throw new IllegalArgumentException("Demolition needs explicit confirmation");
        if (action == 4 && !tag.getBoolean("confirmed"))
            throw new IllegalArgumentException("Replacement needs explicit confirmation");
        Scan scan = StructureScanner.scan(bridge, player, settings);
        for (Cell cell : scan.cells) {
            if (!player.worldObj.blockExists(cell.x, cell.y, cell.z))
                throw new IllegalArgumentException("Structure contains unloaded chunks");
            if (!player.worldObj.canMineBlock(player, cell.x, cell.y, cell.z)
                || !player.canPlayerEdit(cell.x, cell.y, cell.z, side, held)) {
                throw new IllegalArgumentException("Part of this structure cannot be edited");
            }
            String selected = settings.choices.get(cell.selectionKey);
            if (selected == null) selected = settings.choices.get(cell.group);
            if (selected != null && cell.selected(settings) == null)
                throw new IllegalArgumentException("Selected part is no longer an allowed candidate");
            if (selected != null) settings.enableHatchesForSelection(cell.selected(settings));
        }
        settings.save(held);
        player.inventoryContainer.detectAndSendChanges();
        Job job = new Job();
        job.player = player;
        job.bridge = bridge;
        job.tool = held;
        job.settings = settings;
        job.scan = scan;
        job.x = x;
        job.y = y;
        job.z = z;
        job.side = side;
        job.demolish = action == 3;
        job.replace = action == 4;
        if (!job.demolish) bridge.applyMirror(settings.mirror);
        job.orientation = bridge.alignment == null ? null : bridge.alignment.getExtendedFacing();
        jobs.put(player.getUniqueID(), job);
        reply(player, "structureterminal.started", Integer.toString(scan.cells.size()));
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (int n = 0; n < 4; n++) {
            Runnable task = requests.poll();
            if (task == null) break;
            task.run();
        }
        ticks++;
        if (ticks % 6 != 0) return;
        for (Iterator<Job> it = jobs.values()
            .iterator(); it.hasNext();) {
            Job job = it.next();
            try {
                if (!valid(job)) {
                    reply(job.player, "structureterminal.stopped", "");
                    it.remove();
                    continue;
                }
                if (job.demolish) {
                    demolish(job);
                    if (job.cursor >= job.scan.cells.size()) {
                        reply(job.player, "structureterminal.demolished", job.removed + " / " + job.skipped);
                        it.remove();
                    }
                    continue;
                }
                if (job.replace && job.cursor < job.scan.cells.size()) {
                    SelectedSource source = new SelectedSource(job.player, job.settings, job.scan.byPosition);
                    boolean blocked = false;
                    try {
                        for (int n = 0; n < TerminalConfig.budget && job.cursor < job.scan.cells.size(); n++) {
                            Cell cell = job.scan.cells.get(job.cursor++);
                            if (!job.player.worldObj.blockExists(cell.x, cell.y, cell.z)
                                || !job.player.worldObj.canMineBlock(job.player, cell.x, cell.y, cell.z)
                                || !job.player.canPlayerEdit(cell.x, cell.y, cell.z, 1, job.tool)) {
                                job.skipped++;
                                continue;
                            }
                            StructureScanner.BUILD_CELL.set(cell);
                            Replacement.Result result = Replacement
                                .replace(job.bridge, job.player, job.settings, cell, source);
                            if (result == Replacement.Result.BLOCKED) {
                                blocked = true;
                                break;
                            }
                            if (result == Replacement.Result.REPLACED) job.removed++;
                        }
                    } finally {
                        StructureScanner.BUILD_CELL.remove();
                    }
                    if (blocked) {
                        reply(job.player, "structureterminal.replace_blocked", Integer.toString(job.removed));
                        it.remove();
                    }
                    continue;
                }
                StructureLibAPI.enableInstrument(job);
                StructureScanner.BUILD_LOOKUP.set(job.scan.byPosition);
                int result;
                try {
                    SelectedSource source = new SelectedSource(job.player, job.settings, job.scan.byPosition);
                    result = ((ISurvivalConstructable) job.bridge.constructable).survivalConstruct(
                        job.settings.trigger(),
                        TerminalConfig.budget,
                        ISurvivalBuildEnvironment.create(source, job.player));
                } finally {
                    StructureScanner.BUILD_LOOKUP.remove();
                    StructureScanner.BUILD_CELL.remove();
                    StructureLibAPI.disableInstrument();
                }
                BuildProgress.State state = job.progress.accept(result, TerminalConfig.rounds);
                if (state != BuildProgress.State.RUNNING) {
                    reply(
                        job.player,
                        "structureterminal.result." + state.name()
                            .toLowerCase(java.util.Locale.ROOT),
                        Long.toString(job.progress.placed()));
                    it.remove();
                } else if (job.progress.rounds() % 10 == 0)
                    reply(job.player, "structureterminal.progress", Long.toString(job.progress.placed()));
            } catch (RuntimeException e) {
                reply(
                    job.player,
                    "structureterminal.error",
                    e.getMessage() == null ? e.getClass()
                        .getSimpleName() : e.getMessage());
                it.remove();
            }
        }
        if (ticks % 1200 == 0) lastRequest.clear();
    }

    private boolean valid(Job j) {
        MinecraftServer server = MinecraftServer.getServer();
        NativeBridge current = NativeBridge.resolve(j.player.worldObj, j.x, j.y, j.z, j.side);
        return server != null && server.getConfigurationManager().playerEntityList.contains(j.player)
            && j.player.capabilities.allowEdit
            && current != null
            && current.context == j.bridge.context
            && (j.bridge.alignment == null || j.bridge.alignment.getExtendedFacing() == j.orientation)
            && !j.player.isDead
            && j.player.playerNetServerHandler != null
            && j.player.getHeldItem() == j.tool
            && j.player.worldObj == j.bridge.tile.getWorldObj()
            && !j.bridge.tile.isInvalid()
            && j.player.worldObj.blockExists(j.x, j.y, j.z)
            && j.player.worldObj.getTileEntity(j.x, j.y, j.z) == j.bridge.tile
            && Limits.inReach(j.player.posX, j.player.posY, j.player.posZ, j.x, j.y, j.z, TerminalConfig.reach);
    }

    private void demolish(Job j) {
        World world = j.player.worldObj;
        int remaining = TerminalConfig.budget;
        while (j.cursor < j.scan.cells.size() && remaining-- > 0) {
            Cell cell = j.scan.cells.get(j.cursor++);
            if ((cell.x == j.x && cell.y == j.y && cell.z == j.z) || !world.blockExists(cell.x, cell.y, cell.z))
                continue;
            Block block = world.getBlock(cell.x, cell.y, cell.z);
            int meta = world.getBlockMetadata(cell.x, cell.y, cell.z);
            if (block.isAir(world, cell.x, cell.y, cell.z)) continue;
            TileEntity tile = world.getTileEntity(cell.x, cell.y, cell.z);
            if (block.getBlockHardness(world, cell.x, cell.y, cell.z) < 0
                || !world.canMineBlock(j.player, cell.x, cell.y, cell.z)
                || !j.player.canPlayerEdit(cell.x, cell.y, cell.z, 1, j.tool)
                || !empty(tile)) {
                j.skipped++;
                continue;
            }
            java.util.List<ItemStack> drops = BlockMaterials.recover(world, cell.x, cell.y, cell.z, j.player);
            boolean matches = BlockMaterials.known(cell, block, drops);
            if (!matches || MinecraftForge.EVENT_BUS
                .post(new BlockEvent.BreakEvent(cell.x, cell.y, cell.z, world, block, meta, j.player))) {
                j.skipped++;
                continue;
            }
            if (!world.setBlockToAir(cell.x, cell.y, cell.z)) {
                j.skipped++;
                continue;
            }
            BlockMaterials.give(j.player, drops);
            j.removed++;
        }
    }

    private boolean empty(TileEntity tile) {
        if (tile == null) return true;
        if (!TerminalConfig.demolishTiles) return false;
        if (!(tile instanceof IInventory) && !(tile instanceof IFluidHandler)) return false;
        if (tile instanceof IInventory) {
            IInventory inv = (IInventory) tile;
            for (int slot = 0; slot < inv.getSizeInventory(); slot++)
                if (inv.getStackInSlot(slot) != null) return false;
        }
        if (tile instanceof IFluidHandler) for (ForgeDirection side : ForgeDirection.values()) {
            FluidTankInfo[] tanks = ((IFluidHandler) tile).getTankInfo(side);
            if (tanks != null) for (FluidTankInfo tank : tanks)
                if (tank != null && tank.fluid != null && tank.fluid.amount > 0) return false;
        }
        return true;
    }

    private static void reply(EntityPlayerMP player, String key, String value) {
        MinecraftForge.EVENT_BUS.post(new TerminalResultEvent(player, key, value));
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("key", key);
        tag.setString("value", value);
        TerminalNetwork.channel.sendTo(new TerminalNetwork.Reply(tag), player);
    }
}
