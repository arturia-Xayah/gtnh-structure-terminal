> This mod was ported and developed by **GPT-6 (Codex)**.

<div align="center">

<img src="docs/assets/readme-banner.png" alt="Structure Terminal for GT New Horizons" width="100%">

[简体中文](README.md) · **English**

Bring the GTO structure terminal workflow to GTNH.<br>Preview multiblocks, configure parts, and build, replace or dismantle structures from one interface.

[**Download**](https://github.com/arturia-Xayah/gtnh-structure-terminal/releases/tag/v0.1.3-alpha-GTNH-2.9.0-RC-2) · [Quick start](#quick-start) · [Compatibility](#compatibility) · [Build & develop](docs/BUILDING.md#english)

</div>

---

## Features

| | What you can do |
|---|---|
| **Structure preview** | Rotate, pan, zoom and view individual layers; click a block to choose its part |
| **Part configuration** | Adjust exposed size, mode, coil and glass channels; choose casings by group and hatches by position |
| **Survival building** | Supply materials from your inventory and use the machine's native placement rules; keep matching blocks, stop when materials run out and resume after restocking |
| **Replacement & recovery** | Replace ordinary structural materials and fill gaps, or dismantle and recover them; matching blocks are not charged twice |
| **AE inventory** | Optionally extract existing materials through a linked wireless terminal, without requesting autocrafting; live network extraction remains to be validated |

## Installation

| Component | Target version |
|---|---|
| GTNH | **2.9.0-RC-2** |
| Minecraft / Forge | 1.7.10 / 10.13.4.1614 |
| StructureLib / GTNHLib | 1.4.45 / 0.11.52 |
| Structure Terminal | 0.1.3-alpha |

1. Download `structureterminal-0.1.3-alpha-GTNH-2.9.0-RC-2.jar` from [Releases](https://github.com/arturia-Xayah/gtnh-structure-terminal/releases/tag/v0.1.3-alpha-GTNH-2.9.0-RC-2).
2. Place it in the instance's `mods` directory on both the client and server. For single-player, install it on the client.
3. Remove older versions of this mod before starting the instance.

Players only need the JAR. Source code and build scripts are available in this repository.

## Quick start

**Get the terminal**: Find **Structure Terminal** in the creative Tools tab. Look for the blue-and-white terminal with a cyan cube on its screen. Registry ID: `structureterminal:structure_terminal`.

**Current recipe**: Multiblock Structure Hologram Projector + Ender Pearl + Book, shapeless crafting.

1. **Open**: Hold the terminal and right-click a placed multiblock controller.
2. **Configure**: Adjust size, mode and channels in the right panel, then select coils, glass or casings. Choose hatches by clicking individual positions in the preview, so an entire group is not assigned the same hatch.
3. **Supply**: Put materials in your inventory. For AE inventory supply, carry a linked, powered wireless terminal within range and have extraction permission on its network.
4. **Run**: Project the structure into the world or select **Build / Replace & fill / Dismantle**. Replacement and dismantling require a second confirmation. Use **Stop** to cancel the active task.

### View controls

| Input | Action |
|---|---|
| Left-drag | Rotate |
| Right- or middle-drag | Pan |
| Mouse wheel | Zoom; scroll a long list when pointing at the right panel |
| Click a block | Choose the part at that position |
| `F` button | Fit the structure to the view |
| Layer selector | Switch between the full structure and a single layer |

## Compatibility

- **Machine interfaces**: Preview requires a readable StructureLib definition; survival construction requires the machine's native survival builder. Hatch counts, orientation, voltage tiers and special materials still follow the machine's own rules.
- **Existing structures**: Filling gaps preserves matching blocks. Replacement and default dismantling skip TileEntities, including hatches and containers, and operate on recognized structural materials.
- **Running machines**: Normal preview and Shift-right-click open the preview without actively forcing a native structure check or changing the actual orientation. Replacement, dismantling or orientation changes affect the real structure and may stop a machine.
- **UI & models**: The current preview uses block-icon cubes. Complex models, structure trees, mounted-machine navigation and AE pattern encoding are not currently provided.
- **Validation scope**: Headless structure operations have been tested. Real running recipes, the client GUI and live AE extraction remain to be validated. See the per-controller results for special machines and recorded failures.

[**Detailed compatibility**](COMPATIBILITY.md) · [**Per-controller results**](verification/machine-coverage.csv) · [Validation record](VERIFICATION.md)

## Configuration

File: `config/structureterminal.cfg`. Restart the instance after editing it.

| Setting | Default | Purpose |
|---|---:|---|
| `limits.maxPreviewCells` | 100000 | Maximum scanned positions |
| `limits.maxScanMillis` | 500 | Scan time budget in milliseconds |
| `limits.placementBudget` | 25 | Requested native placement budget per round |
| `limits.controllerReach` | 8 | Maximum player distance from the controller |
| `features.allowDemolition` | true | Enable confirmed dismantling |
| `features.allowTileEntityDemolition` | false | Keep disabled to skip all TileEntities during dismantling |

One construction round runs every 6 server ticks, with up to 4 active tasks. If a large structure reaches a scan limit, adjust the budget for your server. Further settings and testing instructions are in the [developer guide](docs/BUILDING.md#english).

## Project resources

[Build & develop](docs/BUILDING.md#english) · [Headless test procedure](verification/PACK_TESTING.md) · [Running state & structure checks](STATE_CHECKS.md)

The interface is based on the GTO structure terminal, and the in-game icon and header use a blue-and-white design inspired by the GTO terminal silhouette. Code and artwork licenses, sources and upstream credits are recorded in [LICENSE](LICENSE), [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [LICENSES](LICENSES).
