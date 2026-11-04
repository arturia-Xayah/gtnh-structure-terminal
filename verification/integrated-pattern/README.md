# Integrated Pattern verification — GTNH 2.9.0

- 161 unit tests, zero failures/errors. Includes pinned AE client search/validation descriptor checks and the terminal cursor guard selector.
- 177 dedicated-server AE checks, all passed, Java 25.0.4.1. Report: [ae-order-report.json](ae-order-report.json).
- 216 production classes exactly match the fixture, all class-file version 52. No pack-test classes in the installable JAR. Artifact hashes: [manifest.json](manifest.json).
- No Minecraft client launched. Actual in-game GUI rendering/click playback remains untested.

The new checks exercise 36-slot physical storage, rejected blanks/nesting/junk, item/fluid/crafting acceptance, one-per-slot Shift insertion, locked hotbar/throw/number-key actions, immediate extraction persistence, stale-holder rejection, NBT round trip, native block/part and dual block/part recipe expansion, stable details across refresh, disabled slots, native priority, actual iron delivery into a chest, exactly-once holder drops, native tile save/load, Memory Card exact costs and atomic shortage handling, Interface Terminal whole-holder extraction/insertion, the IV recipe and EV rejection, and Matter Manipulator exact-NBT item classification. The packing suite also transports a populated holder.

Standard, Lite and the opt-in strict optimizer all plan a child recipe and produce a bill containing its iron input and one requested output. This isolated planning network intentionally has no supplied iron: these tests verify calculation, not completion of a production job. Native item dispatch is tested separately against a real chest. Uninstalling a holder removes its child recipes. Existing rename, security, batch dispatch and packing regressions also pass.

Limits: client search/indicators checked structurally, not by starting a client; native tile/package NBT round trips are not a full process-restart test. Typed fluid pattern expansion is tested; this feature-specific fixture does not complete a fluid job in a machine. No claim of universal support for arbitrary third-party pattern consumers or of large-network performance.

Reproduction: build with `bash work/run_terminal_build.sh spotlessApply build -PmodernizationPackTests=true`, copy the resulting fixture JAR out of build/libs, run `tools/run_modernization_backend.py` with `--suites ae` against GTNH 2.9.0 in a fresh directory, then build the production JAR with `spotlessCheck build prepareUpdateAsset`. Source fixture: `src/optimizerPackTest/java/dev/aeoptimizer/packtest/IntegratedPatternPackChecks.java`.
