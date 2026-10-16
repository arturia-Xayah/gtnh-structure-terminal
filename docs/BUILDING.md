# 开发与构建 / Building & development

[中文首页](../README.md) · [English README](../README.en.md)

## 中文


macOS / Linux 需要 Python 3、Bash 及网络连接：

```sh
chmod +x build.sh
./build.sh spotlessApply build
```

脚本优先使用 `STRUCTURE_TERMINAL_JAVA25`、`STRUCTURE_TERMINAL_JAVA17`、`STRUCTURE_TERMINAL_JAVA8` 指定的 JDK；没有配置时自动下载项目本地 Azul JDK。Gradle 运行使用 JDK 25，旧版工具链需要 JDK 8 / 17，发布类文件统一为 Java 8。JDK 与 Gradle 缓存只保存在 `.tools/` 或指定的 `GRADLE_USER_HOME`，不会改动系统默认 Java。

若旧 Mojang / Forge 下载域名不可用，可运行：

```sh
./build.sh --mirror spotlessApply build
```

镜像辅助脚本用固定哈希校验 Minecraft 1.7.10、Forge userdev、MCP 与 Fernflower 构建资源。Gradle 分发包也有 SHA-256 校验。初次构建需下载较大的开发依赖；离线状态只能复用已有缓存。

Windows 可手动安装上述 JDK，使用 `gradlew.bat build` 并配置 Gradle toolchain 路径；自动安装脚本目前支持 macOS / Linux。

`build` / `check` **不启动游戏**。包括单元测试、Checkstyle、Spotless 及 `verifyDistribution`。后者检查发布 JAR 中所有类均不高于 Java 8 字节码、没有测试夹具、核心插件清单正确。标准构建输出位于 `build/libs/`，交付副本位于 `dist/`。

显式运行无界面服务器集成测试：

```sh
./build.sh runServer -PterminalServerTests=true
```

该选项加载 `src/serverTest/` 中的夹具，生成专用的 `build/headless-tests/terminal-test-world` 世界，只绑定本机随机端口，使用 `nogui` 与 `java.awt.headless=true`，不会启动客户端。测试通过实际服务器 tick 和同一套 `BuildService` 执行，逐项检查世界方块、背包及掉落物；结束自动关闭。结果写入 `build/headless-tests/terminal-server-tests.json`，任何用例失败会令 Gradle 任务失败。夹具不会进入发布 JAR。

## 接口审计工具

工程附带 `/structureterminal audit` 及 `/structureterminal inspect x y z` 管理员命令，供以后人工运行整合包时导出实际注册表和单机结构候选。这些命令不自动启动游戏；本次没有执行。报告中的接口能力标记不等于游戏内测试通过。完整整包逐台后台测试与配置复测的方法见 [PACK_TESTING.md](../verification/PACK_TESTING.md)。

新增专用兼容层时，优先扩展 `bridge/StructureScanner.java` 与 `bridge/NativeBridge.java`，保留原机 `survivalConstruct` 的放置规则。GUI 在 `client/`；AE 取料适配在 `server/AeSupply.java`。


## English

On macOS or Linux, install Python 3 and Bash, then run:

```sh
./build.sh spotlessApply build
```

The launcher installs project-local Azul JDKs when needed. Gradle uses Java 25; legacy tasks use Java 8 and 17. Set `STRUCTURE_TERMINAL_JAVA25`, `STRUCTURE_TERMINAL_JAVA17` and `STRUCTURE_TERMINAL_JAVA8` to reuse local installations. Artifacts target Java 8 bytecode. Downloads and caches remain in `.tools/` or the configured Gradle cache.

If legacy download endpoints are unavailable, use `./build.sh --mirror spotlessApply build`. The mirror helper verifies pinned dependency hashes. Windows users can configure the JDK toolchains manually and run `gradlew.bat build`.

`build` and `check` run formatting, static checks, unit tests and distribution validation; they do not start Minecraft. Output JARs are in `build/libs/`.

To explicitly run the headless integration fixtures:

```sh
./build.sh runServer -PterminalServerTests=true
```

This starts an isolated dedicated test server with `nogui`, uses real server ticks and shuts down automatically. Test fixtures are excluded from normal releases. For the full GTNH controller sweep and configuration trials, see [PACK_TESTING.md](../verification/PACK_TESTING.md).

The administrative commands `/structureterminal audit` and `/structureterminal inspect x y z` export registry and controller capabilities. Capability detection is separate from a successful structure operation.

The native interface bridge is in `bridge/NativeBridge.java` and `bridge/StructureScanner.java`; the UI is in `client/`, and optional AE supply integration is in `server/AeSupply.java`.
