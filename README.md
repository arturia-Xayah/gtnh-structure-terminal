# Structure Terminal — GTNH 1.7.10 移植工程

独立 Mod，版本 **0.1.2-alpha**。参考 GTO 当前公开 `gto` 分支的新结构终端，实现 Minecraft 1.7.10 / GTNH 的结构读取、配置界面与原生生存搭建桥接。

**这是 Alpha 实现，尚未达到“所有 GTNH 多方块完全兼容、GUI 与 GTO 完全一致”的完成标准。** 已执行真实后台服务器夹具测试，并对官方 GTNH 2.9.0-RC-2 整包的 GT 注册表多方块开展逐台测试。逐台结果和未通过项见 [验证记录](VERIFICATION.md)。全量 336 项已尝试，基线 160 项通过适用的完整操作检查，配置复测新增 19 项通过；详细未通过项仍保留。具体边界见 [COMPATIBILITY.md](COMPATIBILITY.md)。

## 安装与取得

目标环境为 GTNH **2.9.0-RC-2** 对应的 Minecraft 1.7.10 / Forge 10.13.4.1614、StructureLib **1.4.45**。模组需要客户端与服务端同时安装，单人游戏安装在客户端即可。普通玩家安装只需 JAR，不需要构建用 JDK 或 Python。

将 `dist/structureterminal-0.1.2-alpha.jar` 放入实例的 `mods` 目录。不要把 `-dev.jar` 或 `-sources.jar` 当作游戏模组安装，也不要同时装两个版本。本工程不捆绑 Minecraft、GTNH、AE2 或 StructureLib。

物品注册名为 `structureterminal:structure_terminal`，显示名为“结构终端”，暂用原版地图图标，位于工具创造标签页。若 StructureLib 投影仪注册成功，提供临时无序配方：**多方块结构投影仪 + 末影珍珠 + 书**。该配方尚未作 GTNH 科技阶段平衡。

## 使用

1. 手持终端右键已放置的多方块控制器。控制器需要公开 StructureLib 结构接口。
2. 在右侧设置尺寸 / 模式及子信道。玻璃、线圈等候选物品来自当前已安装模组注册的数据，不使用 GTO 的物品 ID 或机器表。
3. 在部件列表中选择普通外壳等成组材料；仓室位置请点击预览中的具体方块选择，避免把整组位置指定成一种仓室。
4. 左键拖动旋转，右键或中键拖动平移，滚轮缩放。`F` 按钮适配视图；可切换显示层。右侧卡片内容过长时滚轮滚动。
5. “投影”调用原机的提示投影；“搭建”提交服务端任务。背包先供料，缺料时停止，补料后可再次搭建。“停止任务”取消当前任务。
6. 操作按钮可切换“搭建 / 替换并补建 / 拆除”。替换和拆除都需要再次点击确认。替换仅处理可识别的普通结构材料，已符合目标的方块不重复消耗；当前方块缺料或放置失败会保留或恢复原状。普通方块回收按服务端物品映射返回完整结构材料，包含玻璃；背包满时掉落在玩家附近。
7. 默认跳过所有 TileEntity，替换始终跳过 TileEntity。运行中的机器若被实际替换、拆除或改变朝向，可能因结构变化而停机。

普通预览与 Shift 右键都不调用原生 `checkStructure(true, ...)`。扫描和镜像预览只变换坐标，不调用机器朝向设置方法。**目前 Shift 右键仍打开预览，没有另行绑定“强制原生重检”功能。** 后台夹具已验证扫描不改变运行状态、进度持续增长；这不能代替对任意附属 Mod 的 `construct` 实现做逐台检查。

“使用 AE 库存”是可选项：目标接口为 GTNH AE2 **rv3-beta-1080-GTNH**。玩家需要携带已绑定、已供电且在有效范围内的无线终端，并具备网络取出权限；只取网络现有库存，不发起自动合成。此路径通过 API 编译检查，但未完成实际网络取料验收。

部分老结构只提供原生搭建而不提供可枚举候选材料，界面会标记“由原生搭建器处理”。这类结构保留原机搭建行为，不能据此声称候选选择完整。不能读取结构或没有生存搭建实现的机器会明确拒绝相应操作。StructureLib 的默认 survival 接口不代表机器真正实现了生存搭建，终端会检查实际覆盖的方法，避免默认方法递归。仓室候选会合并链式结构的普通外壳与谓词候选；明确选择仓室后，自动启用 GT 原生 `gt_hatch` 规则（已有设置保留），数量与朝向仍由原机控制。

## 配置

Forge 配置文件为 `config/structureterminal.cfg`，修改后重启实例。主要配置：

| 配置 | 默认 | 含义 |
|---|---:|---|
| `limits.maxPreviewCells` | 100000 | 扫描位置上限 |
| `limits.maxScanMillis` | 500 | 扫描时间预算，防止正常遍历无限扩展；不能中断卡在模组内部的单次调用 |
| `limits.placementBudget` | 25 | 每轮请求原生搭建器最多放置的数量；实际行为由机器实现决定 |
| `limits.maxBuildRounds` | 10000 | 任务轮数上限；无进展会提前停止 |
| `limits.controllerReach` | 8 | 搭建期间玩家与控制器最大距离 |
| `features.allowDemolition` | true | 允许二次确认后的拆除 |
| `features.allowTileEntityDemolition` | false | 可检查为空的容器是否允许拆除；尚未验证盖板和各模组容器，不建议开启 |

每 6 个服务端 tick 执行一轮搭建，最多同时 4 个任务。设置保存在终端物品 NBT 中。机器权限、已加载区块、持有的终端、距离及方向会在服务端检查；材料选择由服务端重建结构后校验。

## 构建与无游戏验证

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

工程附带 `/structureterminal audit` 及 `/structureterminal inspect x y z` 管理员命令，供以后人工运行整合包时导出实际注册表和单机结构候选。这些命令不自动启动游戏；本次没有执行。报告中的接口能力标记不等于游戏内测试通过。完整整包逐台后台测试与配置复测的方法见 [PACK_TESTING.md](verification/PACK_TESTING.md)。

新增专用兼容层时，优先扩展 `bridge/StructureScanner.java` 与 `bridge/NativeBridge.java`，保留原机 `survivalConstruct` 的放置规则。GUI 在 `client/`；AE 取料适配在 `server/AeSupply.java`。

## 来源与许可

本工程是非官方移植，未获 GTO / GTNH 官方背书。参考公开分支快照 `16abc4041714b0098324b86e10d119a2c84fa720`，包含 GTO / LDLib2 的 Ore UI 图集，工程代码采用 LGPL-3.0-or-later。来源、上游许可与原始 MIT 构建模板声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)、[LICENSE](LICENSE) 及 `LICENSES/`。
