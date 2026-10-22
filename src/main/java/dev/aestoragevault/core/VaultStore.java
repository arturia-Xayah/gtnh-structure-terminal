package dev.aestoragevault.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

/** Server-owned ledger. Unlimited logical types, exact fractional-byte accounting, durable import receipts. */
public final class VaultStore implements Closeable {

    private static final int MAGIC = 0x41565332;
    private static final int MAX_RECORD = 64 * 1024 * 1024;
    private static final BigInteger UNITS = BigInteger.valueOf(2048);
    private final Path snapshot;
    private final Path journal;
    private final UUID id;
    private final Map<Resource, Long> stock = new HashMap<>();
    private final Set<Resource> outputOrder = new LinkedHashSet<>();
    private final Set<UUID> receipts = new HashSet<>();
    private final RandomAccessFile wal;
    private FileLock lock;
    private boolean needsFlush;
    private BigInteger used = BigInteger.ZERO;
    private long capacity;
    private long sequence;
    private boolean failed;
    private long pendingBytes;

    public VaultStore(Path folder, UUID id, boolean create) throws IOException {
        this.id = id;
        Files.createDirectories(folder);
        snapshot = folder.resolve(id + ".snapshot");
        journal = folder.resolve(id + ".wal");
        if (!create && (!Files.exists(snapshot) || !Files.exists(journal)))
            throw new IOException("Missing external inventory " + id + "; refusing to create an empty replacement");
        if (Files.exists(snapshot)) readSnapshot();
        wal = new RandomAccessFile(journal.toFile(), "rw");
        try {
            lock = wal.getChannel()
                .tryLock();
            if (lock == null) throw new IOException("Inventory is already open in another process");
            replay();
            pendingBytes = wal.length();
            if (create && !Files.exists(snapshot)) checkpoint();
        } catch (IOException | RuntimeException e) {
            wal.close();
            throw e;
        }
    }

    public synchronized long capacity() {
        return capacity;
    }

    public synchronized BigInteger usedUnits() {
        return used;
    }

    public synchronized long usedBytesCeil() {
        return used.add(UNITS.subtract(BigInteger.ONE))
            .divide(UNITS)
            .longValueExact();
    }

    public synchronized int types() {
        return stock.size();
    }

    public synchronized long amount(Resource r) {
        return stock.getOrDefault(r, 0L);
    }

    public synchronized Map<Resource, Long> entries() {
        return new HashMap<>(stock);
    }

    /** Bounded, fair traversal. No full-inventory copy/sort on the server tick. */
    public synchronized List<Resource> nextOutputBatch(int limit) {
        if (limit < 1 || limit > 256) throw new IllegalArgumentException("Invalid scan budget");
        List<Resource> result = new ArrayList<>(Math.min(limit, outputOrder.size()));
        Iterator<Resource> it = outputOrder.iterator();
        while (it.hasNext() && result.size() < limit) {
            result.add(it.next());
            it.remove();
        }
        outputOrder.addAll(result);
        return result;
    }

    public synchronized boolean hasReceipt(UUID receipt) {
        return receipts.contains(receipt);
    }

    public synchronized long insert(Resource r, long requested, boolean simulate) throws IOException {
        healthy();
        if (requested <= 0) return 0;
        BigInteger free = BigInteger.valueOf(capacity)
            .multiply(UNITS)
            .subtract(used);
        long fit = free.signum() <= 0 ? 0
            : free.divide(BigInteger.valueOf(r.units()))
                .min(BigInteger.valueOf(Long.MAX_VALUE))
                .longValue();
        long added = Math.min(requested, Math.min(fit, Long.MAX_VALUE - amount(r)));
        if (added > 0 && !simulate) change(r, added);
        return added;
    }

    public synchronized long extract(Resource r, long requested, boolean simulate) throws IOException {
        healthy();
        long taken = Math.min(Math.max(0, requested), amount(r));
        if (taken > 0 && !simulate) change(r, -taken);
        return taken;
    }

    private void change(Resource r, long delta) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(1);
        writeResource(out, r);
        out.writeLong(delta);
        append(bytes.toByteArray(), false);
        apply(r, delta);
    }

    /** One journal transaction credits capacity, imported contents and every source token together. */
    public synchronized void absorb(UUID receipt, long extraBytes, Map<Resource, Long> contents) throws IOException {
        healthy();
        if (receipts.contains(receipt)) return;
        if (extraBytes <= 0) throw new IllegalArgumentException("Non-positive cell capacity");
        long nextCapacity = Math.addExact(capacity, extraBytes);
        BigInteger nextUsed = used;
        for (Map.Entry<Resource, Long> e : contents.entrySet()) {
            if (e.getValue() <= 0) throw new IllegalArgumentException("Invalid imported amount");
            Math.addExact(amount(e.getKey()), e.getValue());
            nextUsed = nextUsed.add(
                BigInteger.valueOf(e.getValue())
                    .multiply(
                        BigInteger.valueOf(
                            e.getKey()
                                .units())));
        }
        if (nextUsed.compareTo(
            BigInteger.valueOf(nextCapacity)
                .multiply(UNITS))
            > 0) throw new IllegalArgumentException("Cell contents exceed new logical capacity");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(2);
        writeUuid(out, receipt);
        out.writeLong(extraBytes);
        out.writeInt(contents.size());
        for (Map.Entry<Resource, Long> e : contents.entrySet()) {
            writeResource(out, e.getKey());
            out.writeLong(e.getValue());
        }
        append(bytes.toByteArray(), true);
        capacity = nextCapacity;
        receipts.add(receipt);
        for (Map.Entry<Resource, Long> e : contents.entrySet()) apply(e.getKey(), e.getValue());
    }

    private void apply(Resource r, long delta) {
        long next = Math.addExact(amount(r), delta);
        if (next < 0) throw new IllegalArgumentException("Negative stock");
        if (next == 0) {
            stock.remove(r);
            outputOrder.remove(r);
        } else {
            stock.put(r, next);
            outputOrder.add(r);
        }
        used = used.add(
            BigInteger.valueOf(delta)
                .multiply(BigInteger.valueOf(r.units())));
    }

    private void append(byte[] body, boolean durable) throws IOException {
        if (body.length > MAX_RECORD) throw new IOException("Import batch exceeds 64 MiB; source retained");
        long next = Math.addExact(sequence, 1);
        CRC32 crc = checksum(next, body);
        try {
            wal.seek(wal.length());
            wal.writeInt(body.length);
            wal.writeLong(next);
            wal.write(body);
            wal.writeInt((int) crc.getValue());
            pendingBytes += body.length + 16L;
            needsFlush = true;
            if (durable) {
                wal.getFD()
                    .sync();
                needsFlush = false;
            }
            sequence = next;
        } catch (IOException e) {
            failed = true;
            throw e;
        }
    }

    private void replay() throws IOException {
        long offset = 0, length = wal.length();
        while (offset < length) {
            wal.seek(offset);
            if (length - offset < 12) {
                wal.setLength(offset);
                break;
            }
            int size = wal.readInt();
            long seq = wal.readLong();
            if (size < 1 || size > MAX_RECORD) throw new IOException("Corrupt inventory journal length");
            if (length - offset < size + 16L) {
                wal.setLength(offset);
                break;
            }
            byte[] data = new byte[size];
            wal.readFully(data);
            int crc = wal.readInt();
            if ((int) checksum(seq, data).getValue() != crc)
                throw new IOException("Inventory journal checksum failure");
            if (seq > sequence) {
                if (seq != sequence + 1) throw new IOException("Inventory journal sequence gap");
                applyRecord(data);
                sequence = seq;
            }
            offset += size + 16L;
        }
        if (used.signum() < 0 || used.compareTo(
            BigInteger.valueOf(capacity)
                .multiply(UNITS))
            > 0) throw new IOException("Inventory capacity invariant failed");
    }

    private void applyRecord(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        int op = in.readUnsignedByte();
        if (op == 1) {
            Resource r = readResource(in);
            apply(r, in.readLong());
        } else if (op == 2) {
            UUID receipt = readUuid(in);
            if (!receipts.add(receipt)) throw new IOException("Duplicate journal receipt");
            long extra = in.readLong();
            if (extra <= 0) throw new IOException("Invalid imported capacity");
            capacity = Math.addExact(capacity, extra);
            int count = checkedCount(in.readInt());
            for (int i = 0; i < count; i++) {
                Resource r = readResource(in);
                long amount = in.readLong();
                if (amount <= 0) throw new IOException("Invalid imported amount");
                apply(r, amount);
            }
        } else throw new IOException("Unknown journal operation");
        if (in.available() != 0) throw new IOException("Trailing journal bytes");
    }

    public synchronized void flush() throws IOException {
        healthy();
        if (!needsFlush) return;
        try {
            wal.getFD()
                .sync();
            needsFlush = false;
        } catch (IOException e) {
            failed = true;
            throw e;
        }
    }

    public synchronized boolean isHealthy() {
        return !failed;
    }

    /** Snapshot first, journal truncation second. Replay skips entries already covered by the snapshot sequence. */
    public synchronized void checkpoint() throws IOException {
        healthy();
        try {
            checkpointInternal();
        } catch (IOException e) {
            failed = true;
            throw e;
        }
    }

    private void checkpointInternal() throws IOException {
        Path temp = snapshot.resolveSibling(snapshot.getFileName() + ".tmp");
        CRC32 crc = new CRC32();
        try (FileOutputStream file = new FileOutputStream(temp.toFile());
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new CheckedOutputStream(file, crc)))) {
            out.writeInt(MAGIC);
            writeUuid(out, id);
            out.writeLong(sequence);
            out.writeLong(capacity);
            out.writeInt(receipts.size());
            for (UUID receipt : receipts) writeUuid(out, receipt);
            out.writeInt(stock.size());
            for (Map.Entry<Resource, Long> e : stock.entrySet()) {
                writeResource(out, e.getKey());
                out.writeLong(e.getValue());
            }
            out.writeLong(0x454e445641554c54L);
            out.flush();
            new DataOutputStream(file).writeInt((int) crc.getValue());
            file.getFD()
                .sync();
        }
        // No non-atomic fallback: retain the valid old snapshot/journal if the filesystem cannot provide it.
        Files.move(temp, snapshot, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        try (FileChannel directory = FileChannel.open(snapshot.getParent(), StandardOpenOption.READ)) {
            directory.force(true);
        } catch (UnsupportedOperationException | IOException ignored) { /* Not supported on every host filesystem. */ }
        wal.setLength(0);
        wal.getFD()
            .sync();
        pendingBytes = 0;
        needsFlush = false;
    }

    private void readSnapshot() throws IOException {
        CRC32 crc = new CRC32();
        try (DataInputStream in = new DataInputStream(
            new CheckedInputStream(new BufferedInputStream(Files.newInputStream(snapshot)), crc))) {
            if (in.readInt() != MAGIC || !id.equals(readUuid(in)))
                throw new IOException("Inventory snapshot identity mismatch");
            sequence = in.readLong();
            capacity = in.readLong();
            if (sequence < 0 || capacity < 0) throw new IOException("Invalid inventory header");
            int count = checkedCount(in.readInt());
            for (int i = 0; i < count; i++)
                if (!receipts.add(readUuid(in))) throw new IOException("Duplicate import token");
            count = checkedCount(in.readInt());
            for (int i = 0; i < count; i++) {
                Resource r = readResource(in);
                long amount = in.readLong();
                if (amount <= 0 || stock.containsKey(r)) throw new IOException("Invalid inventory entry");
                apply(r, amount);
            }
            if (in.readLong() != 0x454e445641554c54L) throw new IOException("Incomplete inventory snapshot");
            int actualCrc = (int) crc.getValue();
            if (in.readInt() != actualCrc || in.read() != -1)
                throw new IOException("Inventory snapshot checksum failure");
        }
    }

    public synchronized long journalBytesSinceCheckpoint() {
        return pendingBytes;
    }

    private void healthy() throws IOException {
        if (failed) throw new IOException("Inventory is locked after a write failure; restart required");
    }

    @Override
    public synchronized void close() throws IOException {
        try {
            if (!failed) wal.getFD()
                .sync();
        } finally {
            failed = true;
            wal.close();
        }
    }

    private static int checkedCount(int n) throws IOException {
        if (n < 0) throw new IOException("Negative inventory count");
        return n;
    }

    private static void writeUuid(DataOutput out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInput in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static CRC32 checksum(long seq, byte[] bytes) {
        CRC32 crc = new CRC32();
        for (int i = 7; i >= 0; i--) crc.update((int) (seq >>> (8 * i)) & 255);
        crc.update(bytes);
        return crc;
    }

    private static void writeResource(DataOutput out, Resource r) throws IOException {
        out.writeUTF(r.channel);
        out.writeInt(r.perByte);
        writeBytes(out, r.key());
        writeBytes(out, r.payload());
    }

    private static Resource readResource(DataInput in) throws IOException {
        String channel = in.readUTF();
        int rate = in.readInt();
        return new Resource(channel, rate, readBytes(in), readBytes(in));
    }

    private static void writeBytes(DataOutput out, byte[] bytes) throws IOException {
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static byte[] readBytes(DataInput in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > 4 * 1024 * 1024) throw new IOException("Invalid inventory resource size");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return bytes;
    }
}
