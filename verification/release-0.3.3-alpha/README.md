# 0.3.3-alpha release validation / GTNH 2.9.0

The versioned 0.3.3-alpha build passes 161 unit tests and 177 dedicated-server AE checks on Java 25.0.4.1. No client was started. All 216 production classes match the fixture exactly; no pack-test classes are included in the installation JAR. All four bundled mod metadata versions and updater metadata are 0.3.3-alpha; target GTNH is 2.9.0.

See [manifest](manifest.json), [AE report](ae-order-report.json) and [module metadata](modules.json). The previous [Integrated Pattern verification](../integrated-pattern/README.md) describes test coverage and limits. This release reruns the AE suite; unrelated vault/power test histories remain in their existing directories.

Commands: `bash work/run_terminal_build.sh spotlessCheck build -PmodernizationPackTests=true`; copy fixture out of build/libs; run `tools/run_modernization_backend.py --suites ae` against the official GTNH 2.9.0 server in a fresh directory; `bash work/run_terminal_build.sh spotlessCheck build prepareUpdateAsset`. The isolated run is `work/release-0.3.3-backend` in the development workspace.

CI uses `--no-configuration-cache`, matching the validated local build. The metadata-generation task is not configuration-cache compatible; this workflow-only adjustment does not change the tested JAR.
