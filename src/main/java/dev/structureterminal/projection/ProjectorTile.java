package dev.structureterminal.projection;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

public final class ProjectorTile extends TileEntity {

    public Projection gate = Projection.DEFAULT;
    public Projection.Path path = new Projection.Path(gate);
    public long started;
    public boolean open;
    public String frequency = "", stationName = "";
    public boolean showDestination = true;
    public boolean sceneEnabled, snapshot;
    public String scenePeer = "";
    // Derived from the server directory when sending a tile update. Never written to the world save.
    public String destinationName = "";
    // 0 follows this projector; 1..4 are south, west, north, east (Minecraft yaw order).
    public int exitFacing;

    @Override
    public boolean canUpdate() {
        return false;
    }

    public String configure(Projection draft, String channel, int facing, boolean enable) {
        return configure(draft, channel, facing, enable, stationName, showDestination);
    }

    public String configure(Projection draft, String channel, int facing, boolean enable, String name, boolean show) {
        return configure(draft, channel, facing, enable, name, show, sceneEnabled, snapshot);
    }

    public String configure(Projection draft, String channel, int facing, boolean enable, String name, boolean show,
        boolean view, boolean still) {
        String station = name.trim();
        if (station.length() > 32 || station.indexOf('§') >= 0
            || station.chars()
                .anyMatch(Character::isISOControl))
            return "invalidStation";
        String cleaned = channel.trim();
        if (cleaned.length() > 32 || cleaned.chars()
            .anyMatch(Character::isISOControl) || facing < 0 || facing > 4) return "invalid";
        GateDirectory directory = GateDirectory.get();
        if (enable) {
            String failure = directory.open(this, draft, cleaned, station);
            if (!failure.isEmpty()) return failure;
        } else directory.remove(this);
        open = enable;
        frequency = cleaned;
        sceneEnabled = view;
        snapshot = still;
        stationName = station;
        showDestination = show;
        exitFacing = facing;
        gate = draft;
        path = new Projection.Path(gate);
        started = worldObj.getTotalWorldTime();
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        return "";
    }

    public static void encode(Projection g, NBTTagCompound n) {
        n.setInteger("shape", g.shape.ordinal());
        n.setInteger("width", g.width);
        n.setInteger("height", g.height);
        n.setInteger("innerQuarters", g.innerQuarters);
        n.setInteger("primary", g.primary);
        n.setInteger("secondary", g.secondary);
        n.setIntArray("profile", g.profile());
    }

    public static Projection decode(NBTTagCompound n) {
        int s = n.getInteger("shape");
        int[] p = n.getIntArray("profile");
        if (s < 0 || s >= Projection.Shape.values().length || p.length != Projection.ROWS) return Projection.DEFAULT;
        return new Projection(
            Projection.Shape.values()[s],
            n.getInteger("width"),
            n.getInteger("height"),
            n.hasKey("innerQuarters", 3) ? n.getInteger("innerQuarters")
                : (int) Math.round(
                    Math.max(2, Math.min(12, n.getInteger("width"))) * Math.max(20, Math.min(90, n.getInteger("bore")))
                        * .02),
            p,
            n.hasKey("primary", 3) ? n.getInteger("primary") : Projection.ICE_BLUE,
            n.hasKey("secondary", 3) ? n.getInteger("secondary") : Projection.FROST);
    }

    private void readProjection(NBTTagCompound n) {
        gate = decode(n);
        path = new Projection.Path(gate);
        started = n.getLong("started");
        open = n.getBoolean("open");
        frequency = n.getString("frequency");
        sceneEnabled = n.getBoolean("sceneEnabled");
        snapshot = n.getBoolean("snapshot");
        stationName = n.getString("stationName");
        showDestination = !n.hasKey("showDestination") || n.getBoolean("showDestination");
        exitFacing = Math.max(0, Math.min(4, n.getInteger("exitFacing")));
    }

    private NBTTagCompound writeProjection(NBTTagCompound n) {
        encode(gate, n);
        n.setLong("started", started);
        n.setBoolean("open", open);
        n.setString("frequency", frequency);
        n.setBoolean("sceneEnabled", sceneEnabled);
        n.setBoolean("snapshot", snapshot);
        n.setString("stationName", stationName);
        n.setBoolean("showDestination", showDestination);
        n.setInteger("exitFacing", exitFacing);
        return n;
    }

    @Override
    public void readFromNBT(NBTTagCompound n) {
        super.readFromNBT(n);
        readProjection(n);
    }

    @Override
    public void writeToNBT(NBTTagCompound n) {
        super.writeToNBT(n);
        writeProjection(n);
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound data = writeProjection(new NBTTagCompound());
        if (open && !frequency.isEmpty() && worldObj != null && !worldObj.isRemote) {
            GateDirectory.Entry peer = GateDirectory.get()
                .peer(this);
            if (peer != null) {
                data.setString("destinationName", peer.station);
                if (sceneEnabled) data.setString("scenePeer", peer.dim + "/" + peer.x + "/" + peer.y + "/" + peer.z);
            }
        }
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, data);
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        NBTTagCompound data = packet.func_148857_g();
        readProjection(data);
        scenePeer = data.getString("scenePeer");
        destinationName = data.getString("destinationName");
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        // Include the largest unsaved local editor preview as well as the saved shape.
        return AxisAlignedBB.getBoundingBox(xCoord - 6, yCoord - .2, zCoord - 6, xCoord + 7, yCoord + 14, zCoord + 7);
    }

    @Override
    public boolean shouldRenderInPass(int pass) {
        return pass == 1;
    }
}
