# External inventory format · 外部库存格式

Version 1 of the mod uses the **AVS2** binary snapshot format. All integer fields are big-endian Java `DataInput`/`DataOutput` values. File names use the controller UUID, stored in the overworld's save directory even when the machine is in another dimension.

## Snapshot

`UUID.snapshot` contains:

1. Magic `0x41565332` (`AVS2`).
2. UUID: most/least significant `long`.
3. Last applied sequence (`long`), capacity in bytes (`long`, nonnegative).
4. Receipt count (`int`), followed by receipt UUIDs.
5. Resource count (`int`), followed by resource/quantity pairs.
6. Footer `0x454e445641554c54`.
7. CRC32 (`int`) covering all preceding bytes.

A resource is: channel (`writeUTF`), items per byte (`int`), canonical-key length/data, payload length/data. Keys and payloads are individually limited to 4 MiB. Quantity is a positive signed `long`. Item prototypes use stable item registry names, damage and complete nested NBT. Fluid prototypes use registered fluid names and tags. Prototype counts are one; live counts are stored separately.

Compound key order is sorted. Lists are serialized in reverse traversal order from a copy for O(n) traversal with the 1.7.10 API; this preserves list identity and ordering without mutating the original. Payloads are native, uncompressed NBT bytes. Item per-byte rate is 8; fluid rate is 2048 mB. Usage is summed with `BigInteger` in 1/2048-byte units to avoid overflow or fractional rounding.

## Journal

`UUID.wal` is a sequence of records:

- Body size (`int`, 1..64 MiB).
- Sequence (`long`).
- Body.
- CRC32 (`int`) of sequence bytes followed by body.

Operation 1: byte `1`, resource, signed quantity delta. Operation 2: byte `2`, receipt UUID, positive capacity increment, imported entry count, resource/positive-quantity pairs. A receipt credits capacity and all imported items together exactly once within this external ledger. Each import record is forced to stable storage before removing its source.

The loader validates checksums and sequence continuity. An incomplete final record is truncated; a complete record with invalid checksum stops loading. Records already represented by the snapshot sequence are verified then skipped. Checkpoint writes a temporary snapshot, syncs it, atomically replaces the old snapshot, attempts directory sync, then truncates and syncs the journal. There is no non-atomic rename fallback. Normal AE updates flush at end of tick only when dirty.

## Ownership and recovery

The controller's NBT mirrors `MaximumBytes` and stores `VaultId` and `Automatic`; stock never appears in controller, item-drop or description-packet NBT. On load, validated external state is authoritative. If it contains less capacity than the NBT mirror, loading is rejected. An active controller lease prevents concurrent mounting; an OS file lock prevents simultaneous processes from opening the same journal. Unloading/breaking the controller checkpoints, closes and releases its in-memory repository.

The source cell's root NBT receives `aestoragevault_import`. Before an import commit, the dimension is synchronously saved and pending chunk IO completed. A source restored by world rollback is consumed without re-crediting if its token appears in the ledger. Importing modifies a copy while inspecting contents; unsupported sources remain intact.

This is a transactional ledger, **not a distributed transaction spanning vanilla chunks, players, AE and all other mods**. Complete, stopped-world backups should include every related file. Restoring only an old snapshot/journal or only chunks cannot guarantee consistency. A failed import sync may have committed externally; the machine pauses and the persisted receipt resolves retry/restart. Never delete files to clear a lock.

中文：请整体备份世界；遇到 LOCKED 状态保留文件及日志，在停服状态恢复完整备份。只有未写完的末条日志会自动截断，完整记录校验失败时拒绝加载。容量不可退款，拆下主机也不会删除外部文件。
