# External inventory format · 外部库存格式

0.3.0 writes **AVS6** and accepts the preceding local-development AVS5 format. Capacity and every resource quantity are BigInteger. AVS2/AVS3/AVS4 snapshots from 0.2.x are rejected; users must export old stock before upgrading and create a new vault. All fields are big-endian Java DataInput/DataOutput encodings. Files are keyed by the controller UUID and stored in the overworld save directory, including machines placed in other dimensions.

## Snapshot

`UUID.snapshot` contains:

1. Magic `0x41565336` (`AVS6`).
2. UUID: most/least significant long.
3. Last applied sequence (long), nonnegative capacity in bytes (BigInteger).
4. Receipt count (int), followed by receipt UUIDs.
5. Resource count (int), followed by resource / positive BigInteger quantity pairs.
6. Contribution count (int, 0..1024), followed by item registry name (UTF, ≤256 characters), metadata (int), positive BigInteger absorbed count and positive BigInteger credited bytes. Definitions are unique by item + metadata.
7. Cumulative returned bytes (nonnegative BigInteger), then pending-return count (int, 0..1), followed by pending-return records.
8. Footer `0x454e445641554c54`.
9. CRC32 (int), covering all preceding bytes.

A pending return contains its UUID token, a contribution record describing the count/bytes debited, and a length-prefixed opaque destination payload (0..32768 bytes). Production destinations are compressed NBT containing the native output bus UUID, dimension and coordinates. The pending token must already exist in the receipt set. AVS5 has no field 7; its reader initializes returned bytes to zero and an empty outbox, then writes AVS6 at the next checkpoint.

Each resource contains a channel, per-byte rate, canonical key and native NBT payload. Each key/payload is capped at 4 MiB. Item identity preserves registry name, damage and nested NBT; fluid identity uses the native fluid representation. Count is stored separately from the one-unit prototype. Compound keys are sorted; ordered lists retain their identity without mutating the source NBT.

A nonnegative BigInteger is a positive int byte length followed by Java two's-complement `toByteArray()` bytes. Empty, negative and encodings beyond 4096 bytes are rejected before allocation. Capacity and per-resource quantities can therefore use at most 32767 positive bits. Resource quantities in snapshot/import entries must be greater than zero. The extreme bound is enforced before credit or ordinary insertion, without allocating a bound-width subtraction for small values.

Usage remains an exact BigInteger count of 1/2048-byte units: one item costs 256 units and one mB costs one unit. There is no type tax. Native sequences stay long and Java map/type sizes stay int; changing stock counts does not replace every unrelated integer field.

## Journal

`UUID.wal` is a sequence of records:

- Body size (int, 1..64 MiB).
- Sequence (long).
- Body.
- CRC32 of sequence bytes and body.

Operation 1: byte `1`, resource, signed **long delta**. This is a single AE-sized transaction, not the total quantity. The delta is converted to BigInteger before applying to stock. Normal transfers keep the compact fixed-width delta even when the existing quantity exceeds long.

Operation 2: byte `2`, receipt UUID, positive BigInteger capacity increment, entry count, then resource / positive BigInteger quantity pairs. This commits capacity and all migrated contents once. Native cell imports use a bounded long input map converted to this exact representation; repeated imports can accumulate beyond long. The `absorbExact` API also supports full-precision imported quantities.

Operation 3: byte `3`, receipt UUID, positive BigInteger capacity increment, one contribution record (the same item/metadata/count/bytes layout as above), then the resource count and entries from operation 2. The contribution bytes must match the increment. Counts, credited capacity and migrated stock commit in the same fsynced record; receipt deduplication precedes all updates. Operation 2 remains available to internal test/administrative APIs without source attribution; the production input-bus path uses operation 3.

Operation 4: byte `4`, pending-return record. Its fsynced debit atomically reduces the remaining source count, credited bytes and capacity, adds the receipt and records the delivery intent. No stock is removed. Returning `k` of a source with remaining count `N` and bytes `B` debits `ceil(B*k/N)` bytes; fit checks use exact fractional-byte usage. At most one delivery may be pending per vault.

Operation 5: byte `5`, return token UUID. This fsynced acknowledgment removes the pending intent, retaining the deduplication receipt and cumulative returned bytes. While an intent is pending, normal insert/extract/import and further returns are blocked.

Checksums and sequence continuity are validated on replay. An incomplete final record is truncated; complete corrupt records reject loading. Already-checkpointed sequences are checked and skipped. Checkpoints write and sync a temporary snapshot, atomically replace the old snapshot, attempt directory sync, then truncate/sync the journal. There is no non-atomic rename fallback. Dirty ordinary AE changes flush at end of tick.

## Native AE visibility and queries

The native interface reports `min(exact stock, Long.MAX_VALUE)`. Native simulated/actual transfers stay within their long request amount; the actual ledger is updated precisely and the reserve remains available after extraction. Positive AE-stack additions that would overflow saturate at Long.MAX_VALUE; representable sums and negative adjustments retain native semantics. This guard applies whenever the mod is loaded, independently of the order-optimizer component. It does not give native AE plans, packets, UI or other counters arbitrary precision.

Sneak-right-clicking the controller while holding an item reports its exact count and AE-visible count through on-demand translated chat. A filled container also queries its contained fluid. Formatting happens only on the player's request; no periodic full-stock BigInteger packet is added. The native dashboard still shows total bytes/usage/types.

## Ownership and durability

The controller NBT contains `VaultId`, a `MaximumBytesBig` capacity mirror, `LedgerSequence` and `Automatic`, plus `AbsorbedCells` (source item, metadata, BigInteger count and credited bytes); full stock is external. The validated external contribution ledger is authoritative after a rollback; the NBT list is a mirror and does not independently credit capacity. Because legitimate returns reduce capacity, an older chunk mirror is accepted when its capacity does not exceed external current capacity plus cumulative returned bytes, and its sequence is not newer than the external ledger. A future sequence or capacity beyond lifetime credited bytes rejects loading. This is a rollback consistency check, not permission to restore arbitrary individual files. Active-controller leases and OS file locks reject concurrent mounts. Unloading/breaking the controller checkpoints and releases its repository; normal harvesting preserves its UUID binding.

### Return delivery protocol

1. Validate the formed/exclusive structure, reconstruct the empty source prototype and calculate free-capacity / native-slot fit.
2. Assign the ordinary GT output bus a stable identity and synchronously save the dimension before committing an external debit.
3. Fsync operation 4; record the intent and debit capacity/count. Native AE IO and automatic cell absorption are held.
4. Insert the planned empty items into native `mInventory` and add a `VaultReturnDeliveries` token-to-vault entry. The physical items and receipt share the native tile NBT. While this receipt is staged, standard slot reads, extraction, GUI opening and native auto-output are held.
5. Save the dimension and wait for threaded chunk IO, fsync operation 5, then release the bus receipt and refresh the native storage mount.

Recovery checks the original bus UUID and location. An existing matching receipt means the items are already staged and must not be inserted twice. Without a receipt, the intent is delivered once to that original bus. A leftover receipt whose external intent is acknowledged is cleared. Missing/moved/replaced buses or unavailable slots keep the intent pending; they never trigger delivery to a replacement identity. The normal idle tick skips recovery work when there is no intent or staged bus receipt.

This protocol protects the tested normal native inventory paths and restart windows. It does not create an atomic transaction with arbitrary mods bypassing native inventory APIs or independently restored world files. Backups must still include the complete stopped world.

Import receipts prevent re-crediting a source restored by partial chunk rollback. Imports inspect a copy, persist a source token and the dimension before the durable external commit, then consume the source. This is not a distributed transaction across vanilla chunks, players and every mod. Back up and restore the complete stopped world, including snapshots, journals and chunks. Missing/corrupt files lock the inventory instead of replacing it with an empty one. Do not delete files to clear locks.

[数量预评估与限制 / Quantity preflight](QUANTITY_PREFLIGHT.md) · [玩家指南 / Player guide](../features/STORAGE.md)
