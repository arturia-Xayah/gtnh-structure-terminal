> 历史独立版测试文档；当前整合构建请使用 [BUILDING.md](BUILDING.md)。

# Tests

## Unit tests and installable build

```sh
./gradlew build
```

18 unit tests cover stable old voltage IDs, the extended voltage units, exact GT mixin selectors, exact voltage constants, automatic sentinels, overflow/invalid input, conservative fractional conversion, independent colors, permanent clamping, incomplete supply, 5,000 randomized budgets, and editing/save acknowledgement behavior.

`verifyDistribution` checks Java 8 bytecode, the mixin configuration and absence of test fixtures. It does not launch Minecraft.

## Explicit dedicated-server fixture

Use a separate GTNH 2.9.0-RC-2 server directory and a disposable flat test world. The fixture refuses any world name other than `forge-power-validation`. It creates a real DTPF at `(256,128,256)`, adds registered hatches and controlled test recipes, and writes a JSON report before shutting the server down.

```sh
./gradlew build -PforgePowerTests=true
```

Install the resulting remapped main JAR in the isolated server's `mods`. Configure:

```properties
server-ip=127.0.0.1
server-port=0
online-mode=false
level-name=forge-power-validation
level-type=FLAT
generator-settings=2;7,2x3,2;1;
allow-nether=false
spawn-protection=0
```

Start the official dedicated-server launcher with `-Dplasmaforgepower.tests=true -Djava.awt.headless=true` and `nogui`. With the pack's modern Java launcher, for example:

```sh
java -Xms1G -Xmx6G -Dplasmaforgepower.tests=true -Djava.awt.headless=true @java9args.txt -jar lwjgl3ify-forgePatches.jar nogui
```

Read `forge-power-tests.json`. The fixture uses the actual StructureLib construction and GT structure checks, registered UMV energy hatches, native recipe processing and actual server ticks. It calls Matter Manipulator's own analysis/apply implementation and GT's Data Orb save/load operations.

### Disposable runner (0.2.0)

After building the fixture, preserve its JAR outside `build/libs` before rebuilding production. The runner copies configuration and links pack dependencies into a **new** run directory; it never reuses a source-pack world.

```sh
python3 tools/run_power_backend.py --suite fusion \
  --pack /absolute/path/to/GTNH-server \
  --java /absolute/path/to/java \
  --fixture /absolute/path/to/preserved-test.jar \
  --run-dir /absolute/path/to/new-fusion-test-directory
```

Repeat with `--suite forge` and a different new directory for the original forge regression. The fusion report is `fusion-power-tests.json`; the forge report is `forge-power-tests.json`. The fusion suite builds all ten native ordinary/compact Mk-I–V structures and tests budgets, original candidate order, rejection without consumption, full-vs-auto parallel/overclock behavior, NBT/configuration copying, and real Mk-II low/high/low hot transitions. It uses controlled test recipes, not an exhaustive run of all pack recipes. Both latest suites ran on JDK 25.0.4.1.

The restart/removal probes below cover the original forge baseline. They have not been repeated for the new fusion controllers.

### Restart

After a successful suite, restart the same world with `-Dplasmaforgepower.probe=reload` instead of the suite flag. `forge-power-reload.json` verifies the main controller's red/green/automatic settings and the copied incomplete controller's settings.

### Removal

The test-only `PersistenceProbe` class deliberately has no binary dependency on this add-on. Extract just `dev/plasmaforgepower/packtest/PersistenceProbe.class` from the remapped test JAR into a separate probe JAR, remove the add-on JAR from the isolated server, then start with `-Dplasmaforgepower.probe=removed`. `forge-power-removed.json` verifies that the original controller remains, its real structure forms, and native saves omit the add-on configuration.

Rebuild without `forgePowerTests` before producing the installable artifact. Never distribute a fixture JAR as the player download.

## Client scope

No client launcher is invoked by these checks. The native settings panel is constructed on the dedicated server; its game-client rendering, GUI scale and mouse interaction remain a separate manual acceptance check.
