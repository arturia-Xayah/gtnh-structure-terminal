# 0.3.1 — installed-game feedback fixes

Targets GTNH 2.9.0. Player instructions are in the bilingual README and [storage guide](../features/STORAGE.md).

## Terminal interaction and execution

The owner's requested controls take precedence over the current GTO branch: right-click controller projects; sneak-right-click controller sends construction; sneak-right-click air opens configuration for the last loaded target, or global subchannels before a target exists. A dedicated validated packet saves settings without requiring a controller coordinate. Client target references clear when changing worlds.

Normal construction consumes the machine's own `ISurvivalConstructable` implementation, with the scanned cell count as the requested budget. Native `ACCEPT_STOP` phases are drained synchronously in that server tick. Shortages and obstructions stop immediately. Permission, reach, loaded-chunk and candidate checks remain active. There is no scheduled continuation for normal construction.

“One tick” describes scheduling, not a universal 50 ms wall-time guarantee. `maxInstantBuildMillis` defaults to 1,000 ms and stops between native rounds; it cannot interrupt a single synchronous native call. Cell and round limits still apply. Replacement and dismantling retain bounded per-tick work and a stop control.

## GTO reference

Reference commit: `GregTech-Odyssey/GregTech-Modern@ae5c34516b962e93f8779e2e1a40a79bcca6c51b`.

The adapter uses the upstream Ore UI, widget and view atlases, native-sized controls, floating configuration card, switches, candidate selection, minimap and toolbar tooltips. GTNH StructureLib channels replace GTO's structure-tree data model. The Minecraft 1.20/LDLib scene and layout engine is not embedded. No claim of pixel-for-pixel client visual verification is made: no game client was launched.

## Vault StructureLib / NEI integration

`VaultConstructable` registers `IMultiblockInfoContainer<TileController>` during pre-initialization, before BlockRenderer6343 builds its native NEI index. Its public definition includes the controller so the NEI observer world can discover the stack form. The controller element preserves existing orientation, UUID and ledger rather than replacing an existing controller during creative preview construction.

Three variants show storage link, active ME export, and an optional physical cell-return bus. These are example port arrangements: existing ports at other shell positions are retained, and duplicate default roles are replaced with casing when an existing port already supplies that role. Formation and exclusivity remain governed by `VaultStructure.inspectExclusive`.

## Rendering

Unformed blocks use native LuV casing. Formed shell blocks, controller, ME ports and physical buses share native `MACHINE_CASING_FUSION`. The controller uses `OVERLAY_SCREEN`; ME link uses `OVERLAY_ME_INPUT_HATCH`; export uses `OVERLAY_PIPE_OUT`. These are native 16-pixel assets (the screen has vertically stacked animation frames), resolved from the installed GT textures and resource packs.

Client rendering only needs the synchronized `Formed` flag and shell geometry. It no longer performs server hatch ownership/formation inspection through the controller world, which could reject client views before native hatch synchronization and repeatedly scan neighboring structures. Server-side calls retain full inspection. Formation changes immediately mark the controller for synchronization; receiving the description packet invalidates the shell render area. Existing all-face ME port indicators are preserved because legacy ports have no facing setting.

## Verification scope

The checked installation JAR, fixture digest, production class binding and reports are recorded in [verification/terminal-0.3.1](../../verification/terminal-0.3.1/manifest.json).

- Dedicated GTNH 2.9.0 servers only, running Java 25; distributable classes remain Java 8 bytecode.
- Original vault regression and fresh-process restart suite.
- New checks: all three NEI examples in all six orientations, complete construction in one tick, partial construction with relocated existing ports, and a render-cache-like `IBlockAccess` that exposes synchronized controller state without a World or native hatch data.
- Native GT tests: Fluid Drilling Rig II, Electric Blast Furnace, Entropic Processor. Complete/partial construction records world-tick latency; replacement, dismantling, controller retention and item conservation are checked.
- The fixture now stocks additional legal inventory stacks before clicking; it no longer depends on periodic replenishment for immediate construction. Independent simulated clicks clear only that fake player's wall-clock debounce entry during startup catch-up ticks. This is not a physical input/latency test.
- No client startup, OpenGL render, NEI mouse interaction or screenshot comparison was performed. The native NEI registration and construction contract are tested through their backend APIs; the final in-game presentation still needs visual acceptance.

0.3.0's AVS6 storage format, registered IDs, capacities, source history and return receipts are retained.
