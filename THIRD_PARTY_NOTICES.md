# Third-party notices

Structure Terminal is a Minecraft 1.7.10 / GTNH backport of the GTO structure terminal workflow. Upstream code, artwork and build resources are credited below.

* The reference UI is GregTech-Odyssey/GregTech-Modern, branch `gto`, observed commit `16abc4041714b0098324b86e10d119a2c84fa720` on 2026-10-15. Reference classes: `StructurePreviewScreen`, `StructureBuildFlow`, `TerminalModes`, `PatternBuilderPanel`, `OreSprites`, `UITheme`, `UISizes`. The Minecraft 1.20 rendering and layout engine is not included; `client/` implements a 1.7.10 adapter. Upstream license: LGPL-3.0. https://github.com/GregTech-Odyssey/GregTech-Modern
* `assets/structureterminal/textures/gui/ore_styles.png` is the upstream Ore UI atlas, fetched unchanged from `assets/gtceu/textures/gui/uipro/ore_styles.png`. GTO's `OreSprites` identifies the original as Low-Drag-MC/LDLib2 (`textures/gui/ore_styles.png`), LGPL-3.0. The original atlas coordinates and visual constants are used in `OreTheme`. https://github.com/Low-Drag-MC/LDLib2
* `docs/assets/gto-terminal.png` is the unchanged GTO terminal item texture from the same pinned commit, `src/main/resources/assets/gtceu/textures/item/terminal.png`, LGPL-3.0. SHA-256: `af1d11a00d7b9c32cafca7f68a2fd90f5fc9984034a37902919f0581bb8ff530`. The original texture is retained as a design reference. Source: https://github.com/GregTech-Odyssey/GregTech-Modern/blob/16abc4041714b0098324b86e10d119a2c84fa720/src/main/resources/assets/gtceu/textures/item/terminal.png
* The build scaffold and Gradle wrapper come from GTNewHorizons/ExampleMod1.7.10's official starter archive, MIT. Gradle itself is Apache-2.0 and is downloaded separately. https://github.com/GTNewHorizons/ExampleMod1.7.10
* StructureLib 1.4.45 and the GTNH AE2 API are external dependencies and are not embedded in this mod. Runtime construction remains implemented by the installed machine mods. https://github.com/GTNewHorizons/StructureLib and https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial

Project code is distributed under LGPL-3.0-or-later. See `LICENSE` and `LICENSES/GPL-3.0.txt`. Redistribute the corresponding source with any derivative binaries. No closed-source GTOLib code is included.

The original MIT starter notice is retained verbatim in `LICENSES/ExampleMod-MIT.txt` (Copyright (c) 2021 Johann Bernhardt).

* `assets/structureterminal/textures/items/structure_terminal.png` and `docs/assets/terminal-blue-white.png` use the blue-and-white concept approved by the project owner. It was generated with the built-in image generation tool using the GTO terminal as a reference, then resized to a 128×128 transparent item sprite. The README banner combines this sprite with the project’s layout.
