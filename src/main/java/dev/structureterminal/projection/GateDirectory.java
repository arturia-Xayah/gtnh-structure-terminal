package dev.structureterminal.projection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/** Stored with the overworld so an unloaded gate still reserves its frequency and aperture. */
public final class GateDirectory extends WorldSavedData {

    private static final String KEY = "structureterminal_projected_gates";
    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, List<Entry>> channels = new HashMap<>();
    private final Map<Integer, Map<Long, List<Entry>>> chunks = new HashMap<>();

    public GateDirectory(String name) {
        super(name);
    }

    static GateDirectory get() {
        World world = MinecraftServer.getServer()
            .worldServerForDimension(0);
        GateDirectory data = (GateDirectory) world.perWorldStorage.loadData(GateDirectory.class, KEY);
        if (data == null) {
            data = new GateDirectory(KEY);
            world.perWorldStorage.setData(KEY, data);
        }
        return data;
    }

    static final class Entry {

        final int dim, x, y, z, turn;
        final String channel, station;
        final Projection gate;
        final GateSurface surface;

        Entry(int dim, int x, int y, int z, int turn, String channel, String station, Projection gate) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.turn = turn;
            this.channel = channel;
            this.station = station;
            this.gate = gate;
            surface = new GateSurface(gate, x, y, z, turn);
        }

        boolean at(ProjectorTile t) {
            return dim == t.getWorldObj().provider.dimensionId && x == t.xCoord && y == t.yCoord && z == t.zCoord;
        }
    }

    private static int chunk(double p) {
        return ((int) Math.floor(p)) >> 4;
    }

    private List<Entry> bucket(int dim, int cx, int cz) {
        Map<Long, List<Entry>> index = chunks.get(dim);
        if (index == null) return Collections.emptyList();
        return index.getOrDefault(ChunkCoordIntPair.chunkXZ2Int(cx, cz), Collections.emptyList());
    }

    List<Entry> nearby(int dim, double minX, double minZ, double maxX, double maxZ) {
        int a = chunk(minX), b = chunk(maxX), c = chunk(minZ), d = chunk(maxZ);
        if (a == b && c == d) return bucket(dim, a, c);
        List<Entry> first = Collections.emptyList();
        Set<Entry> seen = null;
        for (int i = a; i <= b; i++) for (int j = c; j <= d; j++) {
            List<Entry> list = bucket(dim, i, j);
            if (list.isEmpty()) continue;
            if (first.isEmpty()) first = list;
            else {
                if (seen == null) seen = new HashSet<>(first);
                seen.addAll(list);
            }
        }
        return seen == null ? first : new ArrayList<>(seen);
    }

    private void index(Entry e, boolean add) {
        Map<Long, List<Entry>> map = chunks.computeIfAbsent(e.dim, k -> new HashMap<>());
        GateSurface s = e.surface;
        int minX = chunk(s.acrossX ? s.bounds.getMinX() : s.plane),
            maxX = chunk(s.acrossX ? s.bounds.getMaxX() : s.plane);
        int minZ = chunk(s.acrossX ? s.plane : s.bounds.getMinX()),
            maxZ = chunk(s.acrossX ? s.plane : s.bounds.getMaxX());
        for (int i = minX; i <= maxX; i++) for (int j = minZ; j <= maxZ; j++) {
            long key = ChunkCoordIntPair.chunkXZ2Int(i, j);
            if (add) map.computeIfAbsent(key, k -> new ArrayList<>())
                .add(e);
            else {
                List<Entry> list = map.get(key);
                list.remove(e);
                if (list.isEmpty()) map.remove(key);
            }
        }
        if (!e.channel.isEmpty()) {
            if (add) channels.computeIfAbsent(e.channel, k -> new ArrayList<>())
                .add(e);
            else {
                List<Entry> list = channels.get(e.channel);
                list.remove(e);
                if (list.isEmpty()) channels.remove(e.channel);
            }
        }
        if (add) entries.add(e);
        else entries.remove(e);
    }

    Entry partner(Entry e) {
        List<Entry> pair = channels.get(e.channel);
        if (pair == null || pair.size() != 2) return null;
        return pair.get(0) == e ? pair.get(1) : pair.get(0);
    }

    Entry peer(ProjectorTile tile) {
        List<Entry> pair = channels.get(tile.frequency);
        if (pair != null && pair.size() == 2) {
            if (pair.get(0)
                .at(tile)) return pair.get(1);
            if (pair.get(1)
                .at(tile)) return pair.get(0);
        }
        return null;
    }

    private void refreshLabel(Entry e) {
        if (e == null) return;
        World world = net.minecraftforge.common.DimensionManager.getWorld(e.dim);
        if (world != null && world.blockExists(e.x, e.y, e.z)) world.markBlockForUpdate(e.x, e.y, e.z);
    }

    private Entry existing(ProjectorTile tile) {
        for (Entry e : bucket(tile.getWorldObj().provider.dimensionId, tile.xCoord >> 4, tile.zCoord >> 4))
            if (e.at(tile)) return e;
        return null;
    }

    void remove(ProjectorTile t) {
        Entry old = existing(t);
        if (old != null) {
            Entry previousPeer = partner(old);
            index(old, false);
            refreshLabel(previousPeer);
            markDirty();
        }
    }

    String open(ProjectorTile t, Projection shape, String frequency, String station) {
        Entry next = new Entry(
            t.getWorldObj().provider.dimensionId,
            t.xCoord,
            t.yCoord,
            t.zCoord,
            t.getBlockMetadata() & 3,
            frequency,
            station,
            shape);
        Entry old = existing(t);
        // Only gates within one maximum diameter can intersect. Check before mutating an existing pair.
        for (Entry e : nearby(next.dim, next.x - 13, next.z - 13, next.x + 13, next.z + 13))
            if (e != old && next.surface.overlaps(e.surface)) return "overlap";
        List<Entry> pair = channels.get(frequency);
        if (pair != null && pair.size() - (old != null && old.channel.equals(frequency) ? 1 : 0) >= 2) return "full";
        Entry previousPeer = old == null ? null : partner(old);
        if (old != null) index(old, false);
        index(next, true);
        Entry newPeer = partner(next);
        if (previousPeer != newPeer) refreshLabel(previousPeer);
        if (newPeer != previousPeer || (old != null && !old.station.equals(station))) refreshLabel(newPeer);
        markDirty();
        return "";
    }

    @Override
    public void readFromNBT(NBTTagCompound n) {
        entries.clear();
        channels.clear();
        chunks.clear();
        NBTTagList list = n.getTagList("gates", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound g = list.getCompoundTagAt(i);
            index(
                new Entry(
                    g.getInteger("dim"),
                    g.getInteger("x"),
                    g.getInteger("y"),
                    g.getInteger("z"),
                    g.getInteger("turn"),
                    g.getString("frequency"),
                    g.getString("station"),
                    ProjectorTile.decode(g)),
                true);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound n) {
        NBTTagList list = new NBTTagList();
        for (Entry e : entries) {
            NBTTagCompound g = new NBTTagCompound();
            ProjectorTile.encode(e.gate, g);
            g.setInteger("dim", e.dim);
            g.setInteger("x", e.x);
            g.setInteger("y", e.y);
            g.setInteger("z", e.z);
            g.setInteger("turn", e.turn);
            g.setString("frequency", e.channel);
            g.setString("station", e.station);
            list.appendTag(g);
        }
        n.setTag("gates", list);
    }
}
