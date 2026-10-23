> Ported, developed and integrated by **GPT-6 (Codex)**.

<div align="center">

<img src="docs/assets/terminal-blue-white.png" alt="GTNH Modernization Upgrade — blue-and-white terminal icon" width="128">

# GTNH Modernization Upgrade

**Structure building · AE conveniences · Color-based power limits · ME storage vault**

[简体中文](README.md) · **English** · [Releases](https://github.com/arturia-Xayah/gtnh-structure-terminal/releases)

[![Download JAR](https://img.shields.io/badge/Download_JAR-0.2.3--alpha-4aa8e8?logo=github)](https://github.com/arturia-Xayah/gtnh-structure-terminal/releases/download/v0.2.4-alpha-GTNH-2.9.0-RC-2/gtnh-modernization-upgrade-0.2.4-alpha-GTNH-2.9.0-RC-2.jar)

Minecraft **1.7.10** · GTNH **2.9.0-RC-2**

</div>

## Contents

- [Installation and upgrading](#installation-and-upgrading)
- [Structure Terminal](#structure-terminal): preview, configure, build, replace and dismantle
- [AE order optimization](#ae-order-optimization): opt-in planning checks and bulk dispatch
- [Craftable input badges](#craftable-input-badges): see which ingredients already have patterns
- [Forge and fusion power controls](#forge-and-fusion-power-controls): limit processing power by input color
- [ME Cell Aggregation Storage](#me-cell-aggregation-storage): consume cells for capacity with external inventory files
- [Compatibility and saves](#compatibility-and-saves)
- [Developer documentation](#developer-documentation)

## Installation and upgrading

1. Install `gtnh-modernization-upgrade-0.2.4-alpha-GTNH-2.9.0-RC-2.jar` in `mods`. Multiplayer clients and servers need the same version; install on the client for single-player.
2. Remove older Structure Terminal JARs and standalone **AE Order Optimizer, Plasma Forge Power and AE Storage Vault** JARs. Their features are included; installing both creates duplicate modules.
3. Back up the complete world before upgrading. Keep `data/aestoragevault`: existing terminal/vault items, blocks, inventory IDs and power settings retain their registration namespaces and save locations. Existing vaults must replace the 23 legacy casings with native LuV machine casings before reforming.

Dependencies are supplied by the target pack: GregTech, StructureLib, GTNHLib, AE2, ModularUI2 and UniMixins. The former standalone mods are not required.

## Structure Terminal

Find **Structure Terminal** in NEI. Craft it shapelessly from a **Multiblock Structure Hologram Projector + Ender Pearl + Book**.

1. Hold it and right-click a placed multiblock controller.
2. Preview the structure and configure exposed dimensions, modes, coils, glass and other parts. Click individual positions to choose hatches.
3. Supply materials in your inventory and choose **Build / Replace & fill / Dismantle**. Replacement and dismantling require confirmation in the UI; **Stop** cancels the task.

Left-drag rotates, right/middle-drag pans, the wheel zooms, `F` fits the view and the layer selector shows individual layers. Matching blocks are kept. Hatches, containers and other inventory-bearing blocks are skipped by default during dismantling.

Machines must expose a readable StructureLib definition; survival building follows their native rules. Previewing does not actively stop a running machine. Actual replacement, dismantling or structural changes may stop it. A linked, powered wireless terminal within range can provide an AE inventory supply entry point.

## AE order optimization

Connect an **ME Order Optimizer** to the submitting network. Right-click for detailed reporting. Only one per network; **1 AE/t**, no channel required.

- An active component enables the modified Lite planner and bulk dispatch on supported receivers. Without it, native behavior remains.
- Only the selected route is checked. Cycles and multi-output patterns stop planning with a report. Detail mode also lists alternative patterns and the selected route.
- Keep patterns at their original unit quantities. Bulk insertion does not add machine parallelism; unsupported receivers retain unit dispatch.
- When borrowing patterns across grids, the submitting network controls whether optimization is enabled.

[Recipe, usage and limits](docs/features/AE_ORDERING.md)

## Craftable input badges

Pattern-terminal ingredients show a top-right **+** when the current network has a crafting pattern for them. Hover for an explanation. Covers item/fluid inputs and extended/wireless terminals sharing the native GUI.

No optimizer component is needed. A badge indicates that a pattern exists; it does not guarantee enough materials for the full crafting chain.

## Forge and fusion power controls

Supports the **Dimensionally Transcendent Plasma Forge, ordinary Fusion Mk-I–V and Compact Fusion Mk-I–V**, using their existing controllers.

1. Open the machine GUI and click the blue **P** button.
2. Choose the color of the input hatch, bus or input assembly. Unpainted inputs use their own group.
3. Enter maximum amperage, select a voltage unit and save. **0 / -1 means automatic**.

For example: red `1A UEV`, green `16A UMV`, unpainted automatic. All groups still share the original machine supply; the settings cap their processing budgets.

Edits apply to the next recipe without interrupting the running reaction. Successfully reforming after energy-hatch changes clamps excessive manual limits. Fusion keeps native recipe order, startup energy and continuous-operation behavior; no order-priority scheduler is added.

Matter Manipulator and Data Orb configuration copying are supported. [Detailed usage and fusion notes](docs/features/POWER.md)

## ME Cell Aggregation Storage

Build a **3×3×3 hollow cube**: outward-facing controller at the center of any of the six faces, **23 native LuV machine casings, exactly one ordinary GT input bus and exactly one ME Storage Link Hatch or Export Bus (mutually exclusive)**, with air in the center. No storage cores. All three custom blocks require **LuV assembler recipes**.

- Connect the Storage Link Hatch to powered AE: **one channel, 2 AE/t**. Right-click for the native AE2 priority GUI; native storage access and Reshuffle are supported.
- Alternatively install the **ME Cell Aggregation Storage Export Bus**. It actively exports into its connected AE network, using 36 item/fluid phantom filters with NEI drag-and-drop and fuzzy, inverter and ore-filter cards. Empty filters export everything.
- Insert components, empty cells or filled cells. Contents and installed upgrade cards are migrated before consuming the source and adding its full nominal capacity. The housing is consumed too.
- No artificial type limit or type overhead. Total capacity uses **BigInteger**, removing the 8 EiB ceiling; per-resource counts retain AE2's signed `long` interface. Shared capacity: **8 items or 2048 mB of fluid per byte**.
- Controller and port tooltips include operating instructions. Right-click the controller for usage, status and automatic-absorption controls. Unsupported, creative, void and spatial cells remain in the bus.

Once formed, the entire structure shares a smooth blue/white/violet sci-fi surface connected across each complete face, with functional panels preserved. The bus and interface can occupy any remaining shell position, including top, bottom, edges and corners. Sharing either component between vaults prevents both from forming; buses reserved by a registered native GT multiblock are also rejected. Releasing the conflict allows formation again.

Inventory lives in external world-save files. Normal harvesting preserves the controller UUID; replacing and reforming reconnects it. Back up the whole world. Importing new cells forces a save and may briefly pause the server.

[Structure diagram, supported cells and backup guide](docs/features/STORAGE.md)

## Compatibility and saves

- Target: **GTNH 2.9.0-RC-2**. Use an adapted build for other pack versions.
- The approved blue-and-white terminal icon is retained. Legacy internal module IDs preserve existing saves; all features ship in one JAR.
- Terminal settings remain in `config/structureterminal.cfg`; restart after editing. Vault files remain in `<world>/data/aestoragevault/`.
- The vault currently requires manual construction; Structure Terminal building and NEI multiblock previews are not integrated for it.
- Back up, migrate and restore the complete stopped world. Do not roll back only inventory files or controller NBT. Removing this mod makes its added blocks and inventories unavailable.

## Developer documentation

[Build, architecture, test reports, validation scope and roadmap](docs/development/README.md)

[License](LICENSE) · [Upstream and artwork credits](THIRD_PARTY_NOTICES.md)
