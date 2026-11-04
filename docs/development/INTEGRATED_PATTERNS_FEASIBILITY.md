# Integrated Pattern implementation

Target: GTNH 2.9.0, AE2 rv3-beta-1095-GTNH, AE2FC 1.5.116-gtnh. Released in 0.3.3-alpha (initially developed in the local 0.3.2-alpha worktree). This supersedes the earlier feasibility-only proposal.

## Data and native integration

`structureterminal:integrated_pattern` is a nonstackable ordinary Item, deliberately not an `ICraftingPatternItem`. NBT `IntegratedPatterns` holds up to 36 real one-count ItemStacks with integer `Slot` indices. The hand-held Container writes changes immediately to its original stack, locks its hotbar slot and rejects stale-holder operations. Children retain their native item identity and NBT. Blank patterns, nesting and items without valid pattern details are rejected on insertion.

The shared DualityInterface hook expands the children through each native item's `getPatternForItem`. The host's existing update pass removes stale child details by stack identity and re-adds cached details before posting its normal crafting-change event. Per-slot cache snapshots invalidate on NBT changes and clear on removal. No work is added to the per-tick dispatch path. Native parent-slot priority remains `slot - 36 * interfacePriority`; child order is stable. One holder exposes up to 36 recipes, not one synthetic multi-output recipe.

Normal and AE2FC block/part interfaces use this shared path. Native dispatch sees the real original child details and the actual interface. Standard/Lite and the optional strict optimizer keep their own route selection, power/channel/blocking and batch rules. Recipe count, not holder count, determines planning work. Nine occupied slots can expose 324 recipes; fully upgraded interfaces can expose 1,296.

## Compatibility and editing

- Interface GUI validation widens only native interface pattern slots, retaining native enabled/edit/inventory checks. The ordinary holder is not accepted as an encoded recipe by NEE or molecular assemblers.
- Interface Terminal cursor validation is widened only for inventories owned by DualityInterface. Movement and synchronization remain native. Client input/output search, broken-recipe, type-mismatch and substitution indicators inspect children through the existing native methods. The visible slot still represents the whole holder.
- Take holders out to edit. No live, nested or remotely mutable child inventory is exposed. Multiply/reset tools do not reinterpret the holder as one recipe. Other third-party pattern consumers are not automatically supported.
- Memory Card copies pay one empty holder and one blank per child before any writes for that holder. Material shortage does not consume partial inputs. Vanilla entries are left to the native routine. Creative copying retains native creative semantics.
- Matter Manipulator classifies the holder as an exact-NBT PortableItemStack, requiring the actual populated holder. It cannot synthesize one for a blank pattern.
- Native interface drops, save data and packing tape contain the holder once. Uninstall only after removing real child patterns. Removing/replacing an interface or its patterns during a running job retains native cancellation/stalling limitations.
- Assembly-line rename pattern deduplication checks carried holders as well as expanded network recipes.

## Validation

See `verification/integrated-pattern/` for the exact build/report evidence and limits. Dedicated-server tests exercise the real GTNH pack; the Minecraft client is not started. Client hook descriptors are checked against the pinned AE bytecode. This does not substitute for in-game visual acceptance or certify arbitrary AE addons.

## Artwork

AI-generated variation of AE2's encoded-pattern sprite: gray border, cyan pattern marks and a small purple integration badge. Exported at 32×32 by nearest-neighbor resampling. The original AE reference remains LGPL-3.0-or-later; see THIRD_PARTY_NOTICES.md. UI uses vanilla chest panels.
