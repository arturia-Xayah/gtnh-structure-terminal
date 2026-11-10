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

    private static final int MAGIC = 0x41565336;
    private static final int MAX_RECORD = 64 * 1024 * 1024;
    private static final BigInteger MAX_COUNT = BigInteger.ONE.shiftLeft(CapacityValue.MAX_ENCODED_BYTES * 8 - 1)
        .subtract(BigInteger.ONE);
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger UNITS = BigInteger.valueOf(2048);
    private final Path snapshot;
    private final Path journal;
    private final UUID id;
    private final Map<Resource, BigInteger> stock = new HashMap<>();
    private final Map<String, NativeInventoryView> readViews = new HashMap<>();
    private final Set<Resource> outputOrder = new LinkedHashSet<>();
    private final Set<UUID> receipts = new HashSet<>();
    private final Map<String, CellContribution> contributions = new java.util.TreeMap<>();
    private long contributionRevision;
    private final Map<UUID, PendingReturn> pendingReturns = new HashMap<>();
    private BigInteger returnedBytes = BigInteger.ZERO;
    private final RandomAccessFile wal;
    private FileLock lock;
    private boolean needsFlush;
    private BigInteger used = BigInteger.ZERO;
    private final Map<String, BigInteger> channelUnits = new HashMap<>();
    private final Map<String, Integer> channelTypes = new HashMap<>();
    private BigInteger capacity = BigInteger.ZERO;
    private BigInteger capacityUnits = BigInteger.ZERO;
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

    public synchronized BigInteger capacity() {
        return capacity;
    }

    public synchronized List<CellContribution> contributions() {
        return new ArrayList<>(contributions.values());
    }

    public synchronized boolean hasPendingReturn() {
        return !pendingReturns.isEmpty();
    }

    public synchronized long sequence() {
        return sequence;
    }

    public synchronized BigInteger returnedBytes() {
        return returnedBytes;
    }

    public synchronized List<PendingReturn> pendingReturns() {
        return new ArrayList<>(pendingReturns.values());
    }

    public synchronized CellContribution contribution(String key) {
        return contributions.get(key);
    }

    /** Exact free space, not rounded displayed bytes. Returning k sources removes ceil(B*k/N) bytes. */
    public synchronized BigInteger maxReturn(String key) {
        CellContribution c = contributions.get(key);
        if (c == null || !pendingReturns.isEmpty()) return BigInteger.ZERO;
        BigInteger freeBytes = capacityUnits.subtract(used)
            .divide(UNITS);
        return freeBytes.multiply(c.count)
            .divide(c.bytes)
            .min(c.count)
            .max(BigInteger.ZERO);
    }

    public synchronized PendingReturn beginReturn(UUID token, String key, int requested, byte[] destination)
        throws IOException {
        healthy();
        if (receipts.contains(token)) return pendingReturns.get(token);
        if (!pendingReturns.isEmpty()) throw new IOException("A return is awaiting durable delivery");
        CellContribution c = contributions.get(key);
        if (c == null || requested < 1) return null;
        if (c.bytes.compareTo(c.count) < 0) throw new IOException("Source capacity cannot be split safely");
        BigInteger amount = maxReturn(key).min(BigInteger.valueOf(requested));
        if (amount.signum() == 0) return null;
        BigInteger removed = c.bytes.multiply(amount)
            .add(c.count.subtract(BigInteger.ONE))
            .divide(c.count);
        PendingReturn result = new PendingReturn(
            token,
            new CellContribution(c.item, c.damage, amount, removed),
            destination);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(4);
        result.write(out);
        append(bytes.toByteArray(), true);
        applyReturn(result);
        return result;
    }

    public synchronized void completeReturn(UUID token) throws IOException {
        healthy();
        if (!pendingReturns.containsKey(token)) return;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(5);
        writeUuid(out, token);
        append(bytes.toByteArray(), true);
        pendingReturns.remove(token);
    }

    private void applyReturn(PendingReturn pending) throws IOException {
        CellContribution debit = pending.source, c = contributions.get(debit.key());
        if (c == null || c.count.compareTo(debit.count) < 0
            || c.bytes.compareTo(debit.bytes) < 0
            || capacityUnits.subtract(debit.bytes.multiply(UNITS))
                .compareTo(used) < 0
            || receipts.contains(pending.id)
            || !pendingReturns.isEmpty()) throw new IOException("Invalid return debit");
        BigInteger n = c.count.subtract(debit.count), b = c.bytes.subtract(debit.bytes);
        if (n.signum() == 0) {
            if (b.signum() != 0) throw new IOException("Return source accounting mismatch");
            contributions.remove(c.key());
        } else contributions.put(c.key(), new CellContribution(c.item, c.damage, n, b));
        setCapacity(capacity.subtract(debit.bytes));
        returnedBytes = returnedBytes.add(debit.bytes);
        pendingReturns.put(pending.id, pending);
        receipts.add(pending.id);
        contributionRevision++;
    }

    public synchronized long contributionRevision() {
        return contributionRevision;
    }

    public synchronized void absorbCell(UUID receipt, long extraBytes, Map<Resource, Long> contents, String item,
        int damage, long count) throws IOException {
        Map<Resource, BigInteger> exact = new HashMap<>();
        for (Map.Entry<Resource, Long> entry : contents.entrySet())
            exact.put(entry.getKey(), BigInteger.valueOf(entry.getValue()));
        absorbExact(
            receipt,
            BigInteger.valueOf(extraBytes),
            exact,
            new CellContribution(item, damage, BigInteger.valueOf(count), BigInteger.valueOf(extraBytes)));
    }

    public synchronized BigInteger usedUnits() {
        return used;
    }

    public synchronized BigInteger usedBytesCeil() {
        return used.add(UNITS.subtract(BigInteger.ONE))
            .divide(UNITS);
    }

    /** Constant-time channel accounting; both views share the same physical capacity. */
    public synchronized BigInteger channelUsedBytes(String channel) {
        return channelUnits.getOrDefault(channel, BigInteger.ZERO)
            .add(UNITS.subtract(BigInteger.ONE))
            .divide(UNITS);
    }

    public synchronized int channelTypes(String channel) {
        return channelTypes.getOrDefault(channel, 0);
    }

    public synchronized BigInteger channelCapacity(String channel) {
        return capacity.subtract(usedBytesCeil())
            .add(channelUsedBytes(channel));
    }

    public synchronized int types() {
        return stock.size();
    }

    /** Native AE window; the ledger retains the complete quantity. */
    public synchronized long amount(Resource r) {
        return exactAmount(r).min(MAX_LONG)
            .longValue();
    }

    public synchronized BigInteger exactAmount(Resource r) {
        return stock.getOrDefault(r, BigInteger.ZERO);
    }

    public synchronized Map<Resource, BigInteger> entries() {
        return new HashMap<>(stock);
    }

    /** Stable native quantities; built lazily per channel and incrementally invalidated by committed changes. */
    public synchronized NativeInventoryView.Snapshot nativeSnapshot(String channel) {
        NativeInventoryView view = readViews.get(channel);
        if (view == null) {
            view = new NativeInventoryView();
            for (Map.Entry<Resource, BigInteger> entry : stock.entrySet())
                if (entry.getKey().channel.equals(channel)) view.update(entry.getKey(), entry.getValue());
            readViews.put(channel, view);
        }
        return view.snapshot();
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
        if (requested <= 0 || !pendingReturns.isEmpty()) return 0;
        BigInteger free = capacityUnits.subtract(used);
        long fit = free.signum() <= 0 ? 0
            : free.divide(BigInteger.valueOf(r.units()))
                .min(BigInteger.valueOf(Long.MAX_VALUE))
                .longValue();
        BigInteger present = exactAmount(r);
        // Avoid allocating a 4 KiB subtraction on ordinary IO just to check the parser's extreme limit.
        long room = present.bitLength() < CapacityValue.MAX_ENCODED_BYTES * 8 - 1 ? Long.MAX_VALUE
            : MAX_COUNT.subtract(present)
                .min(MAX_LONG)
                .longValue();
        long added = Math.min(requested, Math.min(fit, room));
        if (added > 0 && !simulate) change(r, added);
        return added;
    }

    public synchronized long extract(Resource r, long requested, boolean simulate) throws IOException {
        healthy();
        if (!pendingReturns.isEmpty()) return 0;
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
        absorb(receipt, BigInteger.valueOf(extraBytes), contents);
    }

    public synchronized void absorb(UUID receipt, BigInteger extraBytes, Map<Resource, Long> contents)
        throws IOException {
        Map<Resource, BigInteger> exact = new HashMap<>();
        for (Map.Entry<Resource, Long> entry : contents.entrySet())
            exact.put(entry.getKey(), BigInteger.valueOf(entry.getValue()));
        absorbExact(receipt, extraBytes, exact);
    }

    /** Full-precision import; the durable receipt credits capacity and quantities as one transaction. */
    public synchronized void absorbExact(UUID receipt, BigInteger extraBytes, Map<Resource, BigInteger> contents)
        throws IOException {
        absorbExact(receipt, extraBytes, contents, null);
    }

    private void absorbExact(UUID receipt, BigInteger extraBytes, Map<Resource, BigInteger> contents,
        CellContribution contribution) throws IOException {
        healthy();
        if (receipts.contains(receipt)) return;
        if (!pendingReturns.isEmpty()) throw new IOException("A return is awaiting durable delivery");
        CellContribution combined = contribution;
        if (contribution != null) {
            CellContribution old = contributions.get(contribution.key());
            if (old == null && contributions.size() >= 1024)
                throw new IllegalArgumentException("Too many absorbed cell definitions");
            if (old != null) combined = old.plus(contribution);
        }
        if (extraBytes.signum() <= 0) throw new IllegalArgumentException("Non-positive cell capacity");
        BigInteger nextCapacity = capacity.add(extraBytes);
        CapacityValue.encode(nextCapacity);
        BigInteger nextUsed = used;
        for (Map.Entry<Resource, BigInteger> e : contents.entrySet()) {
            if (e.getValue()
                .signum() <= 0) throw new IllegalArgumentException("Invalid imported amount");
            CapacityValue.encode(exactAmount(e.getKey()).add(e.getValue()));
            nextUsed = nextUsed.add(
                e.getValue()
                    .multiply(
                        BigInteger.valueOf(
                            e.getKey()
                                .units())));
        }
        if (nextUsed.compareTo(nextCapacity.multiply(UNITS)) > 0)
            throw new IllegalArgumentException("Cell contents exceed new logical capacity");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(contribution == null ? 2 : 3);
        writeUuid(out, receipt);
        CapacityValue.write(out, extraBytes);
        if (contribution != null) contribution.write(out);
        out.writeInt(contents.size());
        for (Map.Entry<Resource, BigInteger> e : contents.entrySet()) {
            writeResource(out, e.getKey());
            CapacityValue.write(out, e.getValue());
        }
        append(bytes.toByteArray(), true);
        setCapacity(nextCapacity);
        receipts.add(receipt);
        if (combined != null) {
            contributions.put(combined.key(), combined);
            contributionRevision++;
        }
        for (Map.Entry<Resource, BigInteger> e : contents.entrySet()) apply(e.getKey(), e.getValue());
    }

    private void apply(Resource r, long delta) {
        apply(r, BigInteger.valueOf(delta));
    }

    private void apply(Resource r, BigInteger delta) {
        BigInteger old = exactAmount(r);
        BigInteger next = old.add(delta);
        if (next.signum() < 0) throw new IllegalArgumentException("Negative stock");
        if (next.signum() == 0) {
            stock.remove(r);
            outputOrder.remove(r);
        } else {
            stock.put(r, next);
            outputOrder.add(r);
        }
        NativeInventoryView view = readViews.get(r.channel);
        if (view != null) view.update(r, next);
        BigInteger units = delta.multiply(BigInteger.valueOf(r.units()));
        used = used.add(units);
        channelUnits.put(
            r.channel,
            channelUnits.getOrDefault(r.channel, BigInteger.ZERO)
                .add(units));
        channelTypes.put(
            r.channel,
            channelTypes.getOrDefault(r.channel, 0)
                + (old.signum() == 0 && next.signum() > 0 ? 1 : old.signum() > 0 && next.signum() == 0 ? -1 : 0));
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
        if (used.signum() < 0 || used.compareTo(capacityUnits) > 0)
            throw new IOException("Inventory capacity invariant failed");
    }

    private void applyRecord(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        int op = in.readUnsignedByte();
        if (op == 1) {
            Resource r = readResource(in);
            apply(r, in.readLong());
        } else if (op == 2 || op == 3) {
            UUID receipt = readUuid(in);
            if (!receipts.add(receipt)) throw new IOException("Duplicate journal receipt");
            BigInteger extra = CapacityValue.read(in);
            if (extra.signum() <= 0) throw new IOException("Invalid imported capacity");
            setCapacity(capacity.add(extra));
            if (op == 3) {
                CellContribution c = CellContribution.read(in);
                if (!c.bytes.equals(extra)) throw new IOException("Contribution capacity mismatch");
                CellContribution old = contributions.get(c.key());
                if (old == null && contributions.size() >= 1024) throw new IOException("Too many cell definitions");
                contributions.put(c.key(), old == null ? c : old.plus(c));
                contributionRevision++;
            }
            int count = checkedCount(in.readInt());
            for (int i = 0; i < count; i++) {
                Resource r = readResource(in);
                BigInteger amount = CapacityValue.read(in);
                if (amount.signum() <= 0) throw new IOException("Invalid imported amount");
                apply(r, amount);
            }
        } else if (op == 4) applyReturn(PendingReturn.read(in));
        else if (op == 5) {
            if (pendingReturns.remove(readUuid(in)) == null) throw new IOException("Missing return intent");
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
            CapacityValue.write(out, capacity);
            out.writeInt(receipts.size());
            for (UUID receipt : receipts) writeUuid(out, receipt);
            out.writeInt(stock.size());
            for (Map.Entry<Resource, BigInteger> e : stock.entrySet()) {
                writeResource(out, e.getKey());
                CapacityValue.write(out, e.getValue());
            }
            out.writeInt(contributions.size());
            for (CellContribution c : contributions.values()) c.write(out);
            CapacityValue.write(out, returnedBytes);
            out.writeInt(pendingReturns.size());
            for (PendingReturn pending : pendingReturns.values()) pending.write(out);
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
            int magic = in.readInt();
            if (magic != MAGIC && magic != 0x41565335)
                throw new IOException("Unsupported inventory snapshot version; AVS5/AVS6 required");
            if (!id.equals(readUuid(in))) throw new IOException("Inventory snapshot identity mismatch");
            sequence = in.readLong();
            BigInteger bytes = CapacityValue.read(in);
            if (sequence < 0 || bytes.signum() < 0) throw new IOException("Invalid inventory header");
            setCapacity(bytes);
            int count = checkedCount(in.readInt());
            for (int i = 0; i < count; i++)
                if (!receipts.add(readUuid(in))) throw new IOException("Duplicate import token");
            count = checkedCount(in.readInt());
            for (int i = 0; i < count; i++) {
                Resource r = readResource(in);
                BigInteger amount = CapacityValue.read(in);
                if (amount.signum() <= 0 || stock.containsKey(r)) throw new IOException("Invalid inventory entry");
                apply(r, amount);
            }
            count = in.readInt();
            if (count < 0 || count > 1024) throw new IOException("Invalid contribution count");
            BigInteger contributed = BigInteger.ZERO;
            for (int i = 0; i < count; i++) {
                CellContribution c = CellContribution.read(in);
                if (contributions.put(c.key(), c) != null) throw new IOException("Duplicate cell definition");
                contributed = contributed.add(c.bytes);
            }
            if (contributed.compareTo(capacity) > 0) throw new IOException("Contribution capacity exceeds total");
            if (magic == MAGIC) {
                returnedBytes = CapacityValue.read(in);
                count = in.readInt();
                if (count < 0 || count > 1) throw new IOException("Invalid pending return count");
                for (int i = 0; i < count; i++) {
                    PendingReturn pending = PendingReturn.read(in);
                    if (!receipts.contains(pending.id) || pending.source.bytes.compareTo(returnedBytes) > 0)
                        throw new IOException("Invalid pending return receipt");
                    pendingReturns.put(pending.id, pending);
                }
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

    private void setCapacity(BigInteger bytes) {
        CapacityValue.encode(bytes);
        capacity = bytes;
        capacityUnits = bytes.multiply(UNITS);
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
