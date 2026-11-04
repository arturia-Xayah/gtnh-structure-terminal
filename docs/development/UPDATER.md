# Client updater (0.3.2)

Player instructions: [中文](../../README.md#客户端更新) / [English](../../README.en.md#client-updates).

## Boundaries and integration

Only `ClientProxy` initializes the updater. Dedicated servers never initialize Minecraft client classes, start HTTP tasks or update themselves. Forge network compatibility checks in the vault and power modules are unchanged.

Main-menu integration uses Forge `InitGuiEvent.Post` and `ActionPerformedEvent.Pre`. It recognizes vanilla `GuiMainMenu` and GTNH Custom Main Menu's `GuiCustom` whose public `guiConfig.name` is `mainmenu`. A free button location is selected near the top right; the existing menu is retained as the update screen's background. The panel uses the GTNH menu's cyan/amber palette, current Minecraft font/resource configuration, wrapping, scrolling and scaled coordinates. CMM is optional, not a hard binary dependency. No client or OpenGL visual verification was performed.

Server probing begins at `GuiOpenEvent` for `GuiConnecting`. FML's `ClientConnectedToServerEvent` is unsuitable: it fires only after successful negotiation. The connecting screen has already assigned current `ServerData`; address/SRV resolution and a bounded Minecraft status request run off-thread. Forge `modinfo.modList` provides the remote version. A result is tied to that connection attempt and only opens a prompt on its disconnected screen. Status-disabled/proxied servers may not expose this information. The normal handshake is never bypassed; this also means a downloaded update needs a fresh client process before joining.

## Selection and transport

Repository is fixed to `arturia-Xayah/gtnh-structure-terminal`. Menu checks inspect up to the most recent 100 Releases, selecting the highest supported semantic version for the exact detected GTNH version and prerelease channel. Exact server lookups use the corresponding release tag, bypassing menu skip/prerelease preferences but never downgrading or substituting a newer release. Required naming:

- Tag: `v<version>-GTNH-<pack>`
- Asset: `gtnh-modernization-upgrade-<version>-GTNH-<pack>.jar`
- GitHub-provided `sha256:` asset digest is mandatory.
- Embedded `modernization-update.json`: `{schema: 1, version: "…", gtnh: "…"}`.

`generateUpdateMetadata` and `verifyDistribution` bind the descriptor to build version. `prepareUpdateAsset` provides the correctly named CI asset. Older release assets without this descriptor cannot be installed by this updater. The descriptor currently declares the build's tested GTNH 2.9.0 target; update it deliberately when porting.

Detection reads `mainmenu:version.txt`, stripping Minecraft color codes and preserving RC qualifiers. An optional explicit override is supported. No pack is inferred from a single mod version. Unknown packs fail closed.

HTTPS is mandatory. Redirects are bounded and restricted to GitHub/API/release asset hosts. Timeouts and size limits apply (2 MiB metadata, 32 MiB JAR); downloads verify length, hash, all four module marker classes, compatibility metadata, and absence of test fixtures. There is no arbitrary server-provided download URL. These checks trust the project GitHub account and TLS; they are not a separate publisher-signature scheme.

## Installation transaction

Downloads and helper files live outside `mods`, under `.modernization-updates/update-…`. Only a real installation JAR in `mods` or `mods/1.7.10` can be replaced; duplicate modernization JARs in the mods tree are rejected.

On explicit Exit and install, the running JAR is copied as `installer.jar`. A new Java process runs the pure-JDK `UpdateInstaller` main class with no Minecraft/Forge/Gson dependencies. The game holds an exclusive `session.lock`; the helper signals readiness, then waits up to 15 minutes for OS release when the game process exits. Failure to start the helper does not exit the game. No early shutdown hook releases this lock.

After the lock is obtained, the helper verifies original/staged hashes, checks duplicates, saves `backup.jar`, verifies the backup, writes and fsyncs a sibling `.pending` file, then atomically replaces the original path. An unsupported atomic move or occupied file fails without falling back to unsafe delete-and-copy. Preserving the original filename prevents a zero-JAR/two-JAR interval and keeps launcher references stable. The Mods screen, not the filename, gives the installed version.

The helper writes `result.properties` and `installer.log`; the next client session shows the previous result in the update screen. Retained backups are not automatically deleted. Manual recovery: close the game, replace the installed file with `backup.jar` from the relevant transaction. No game saves/configuration are migrated or rolled back by this updater. Launchers that kill child processes or replace instance files may prevent installation; manual installation remains available. Windows atomic replacement has not been exercised on this macOS host.

## Validation

Unit coverage includes semantic ordering, exact-server selection, prerelease/pack filtering, untrusted assets/redirects, bounded/cancelled transfers, corrupted/wrong/test JARs, Forge status parsing and a real loopback status exchange. Filesystem and separate-JVM tests exercise delayed installation, backups, duplicate detection and changed source/download rejection. The helper subprocess test starts no Minecraft code.

`tools/check_updater_server.py` starts a disposable official GTNH dedicated server with the normal installation JAR, invokes the production Java status probe, then stops the server. It is not a client handshake/UI test. Evidence is kept under `verification/updater-0.3.2/`.
