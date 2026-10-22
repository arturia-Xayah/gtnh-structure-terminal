# 构建与后台测试 / Building and dedicated-server tests

[开发文档目录](README.md) · [中文首页](../../README.md) · [English README](../../README.en.md)

## 正式构建

macOS / Linux 需要 Python 3、Bash 及网络连接：

```sh
chmod +x build.sh
./build.sh spotlessApply build
```

优先使用 `STRUCTURE_TERMINAL_JAVA25`、`STRUCTURE_TERMINAL_JAVA17`、`STRUCTURE_TERMINAL_JAVA8` 指定的 JDK；否则辅助脚本下载项目本地 Azul JDK。Gradle 运行于 Java 25，旧版工具链需要 Java 8 / 17，运行时代码统一生成 Java 8 字节码。

缓存仅放在 `.tools/` 或 `GRADLE_USER_HOME`，不会修改系统默认 Java。旧域名不可用时可使用 `./build.sh --mirror spotlessApply build`，镜像下载带固定哈希校验。Windows 可手动配置上述工具链后使用 `gradlew.bat build`。

正式安装包为 `build/libs/gtnh-modernization-upgrade-0.2.1-alpha.jar`。交付副本的文件名和目录额外标注 GTNH 版本。`-dev.jar`、`-sources.jar` 及测试夹具不作为玩家安装包。

`build` / `check` 运行格式、静态检查、单元测试和发行检查，**不会启动游戏**。发行检查验证 Java 8 字节码、运行时映射、四个兼容模块的版本/图标、Mixin 配置及无测试类。

## 整合包后台验证

```sh
./build.sh -PmodernizationPackTests=true spotlessApply build
cp build/libs/gtnh-modernization-upgrade-0.2.1-alpha.jar /tmp/modernization-fixture.jar
python3 tools/run_modernization_backend.py \
  --pack /path/to/GTNH-server --java /path/to/java25/bin/java \
  --fixture /tmp/modernization-fixture.jar --run-dir /new/disposable/validation
./build.sh spotlessApply build
```

夹具来自 `src/packTest`、`src/optimizerPackTest`、`src/powerPackTest`、`src/vaultPackTest`、`src/modernizationPackTest`。仅显式测试构建加入；各入口依赖独立服务器参数。统一检查实际四个模块同时加载、版本/父模块/终端图标一致，旧注册物品和方块存在。

测试顺序：仓库存取与跨进程重启、锻炉、普通/压缩聚变、AE 网络、三个结构操作样本。各套创建独立世界；仓库测试第二次使用其同一世界。支持 `--suites vault forge fusion ae structures` 选择范围。每个服务器只绑定本机随机端口，以 `nogui` / `java.awt.headless=true` 运行，单阶段 20 分钟超时。不会复用源服务器的世界，不启动客户端。

原有 `terminalPackTests`、`optimizerPackTests` 选项保留，用于各自历史夹具；统一整合测试优先使用 `modernizationPackTests`。后台报告由统一运行器保存。正式包与测试包的生产 class 逐字节比对，结果见 [整合验证](VALIDATION.md)。

## 实现入口

| 模块 | 入口 |
|---|---|
| 结构预览与建造 | `dev.structureterminal`，桥接在 `bridge/`，执行在 `server/BuildService` |
| AE 下单与批量发配 | `dev.aeoptimizer`；独立 LateMixinLoader 与批量接收协议 |
| 原料＋标记 | `dev.structureterminal.ae`、`client/ae`、`dev.aeoptimizer.mixin.client` |
| 功率控制 | `dev.plasmaforgepower`；独立 LateMixinLoader、GT / ModularUI 适配 |
| 外部存储 | `dev.aestoragevault`；`core/VaultStore`、`ae/`、`storage/Repositories` |

管理员命令 `/structureterminal audit`、`/structureterminal inspect x y z` 导出注册表和单机候选。能力标记不等于机器实测通过。

## English

Run `./build.sh spotlessApply build`. The launcher installs local Java 25/17/8 toolchains or reuses the `STRUCTURE_TERMINAL_JAVA*` overrides. Artifacts target Java 8; build/check never launch Minecraft. The installable file is `build/libs/gtnh-modernization-upgrade-0.2.1-alpha.jar`.

Explicitly opt into `-PmodernizationPackTests=true` for all dedicated-server fixtures, preserve that JAR outside the build output, run `tools/run_modernization_backend.py` with the command above, then rebuild production without the flag. It creates separate disposable server worlds, including a second process for vault persistence, with no client entry point. Legacy module namespaces remain stable; details are in [INTEGRATION.md](INTEGRATION.md).
