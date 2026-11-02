package dev.aestoragevault.core;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.UUID;

/** Durable, already-debited output intent. Destination data is owned by the world adapter. */
public final class PendingReturn {

    public final UUID id;
    public final CellContribution source;
    private final byte[] destination;

    public PendingReturn(UUID id, CellContribution source, byte[] destination) {
        if (destination.length > 32768) throw new IllegalArgumentException("Return destination too large");
        this.id = id;
        this.source = source;
        this.destination = destination.clone();
    }

    public byte[] destination() {
        return destination.clone();
    }

    public void write(DataOutput out) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
        source.write(out);
        out.writeInt(destination.length);
        out.write(destination);
    }

    public static PendingReturn read(DataInput in) throws IOException {
        UUID id = new UUID(in.readLong(), in.readLong());
        CellContribution source = CellContribution.read(in);
        int size = in.readInt();
        if (size < 0 || size > 32768) throw new IOException("Invalid return destination length");
        byte[] bytes = new byte[size];
        in.readFully(bytes);
        return new PendingReturn(id, source, bytes);
    }
}
