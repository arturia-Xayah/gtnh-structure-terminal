# 完整 GTNH 整包后台验证方法

目标固定为 GTNH 2.9.0-RC-2，官方 Java 17–26 服务端，不启动客户端或图形界面。下载来源及 SHA-256 记录在 `full-pack/pack-download.json`。游戏整包、存档、Java 和依赖缓存不随本工程分发。

使用独立目录解压服务端，不在日常游戏存档执行。服务器仅监听 `127.0.0.1`、随机端口，关闭 RCON / query，使用专用平坦世界 `terminal-pack-validation`。测试会主动拆除测试区域、生成材料并操作模拟玩家库存。

## 构建测试包

```sh
./build.sh spotlessApply build -PterminalPackTests=true
```

将 **重混淆输出 `build/libs/structureterminal-0.1.2-alpha.jar`** 安装到隔离服务端 `mods`。不要安装 `-pack-tests.jar`：它是重混淆任务的开发输入。不要同时安装正式终端 JAR。测试包会增加 `structureterminal_packtest`，只允许在明确启用的专用后台测试世界中执行。

准备 `eula.txt` 和 `server.properties` 后，在该隔离服务端目录运行：

```sh
/path/to/java25/bin/java -Xms1G -Xmx6G -XX:+UseG1GC \
  -Dstructureterminal.packTests=true -Djava.awt.headless=true \
  -Duser.language=en -Dfml.readTimeout=180 \
  @java9args.txt -jar lwjgl3ify-forgePatches.jar nogui
```

全量运行自动枚举当前整包 GT 注册表中的 `IConstructable` 及多方块基类条目，逐项执行，然后写入 `machine-validation/` 并关闭服务器。此枚举不覆盖独立于 GT 注册表的所有其他 Mod 多方块。

可在 `@java9args.txt` 前添加这些 JVM 参数进行独立配置复测：

- `-Dstructureterminal.machineIds=141,1000`：只测指定注册 ID。
- `-Dstructureterminal.energyTier.141=3`：为该 ID 供给 HV 能源仓；等级索引从 ULV=0 开始。
- `-Dstructureterminal.explicitHatchOnly=true`：不预设 `gt_hatch=1`，通过明确选择一个仓室验证终端自动启用原生仓室规则。
- `-Dstructureterminal.resumePackTests=true`：从已有连续 JSONL 条目续跑；仅适用于相同注册表和配置，不把不同版本结果拼成一次全新验证。

开始新运行会覆盖机器 JSONL；先归档已有结果。运行中 `report.json` 可能仍是上次结果，必须同时检查 `progress.json` 的 `finished=true`。

## 场景与判据

每台控制器使用默认尺寸 / 模式；全量基线启用 `gt_hatch=1`，库存优先选择普通外壳与基础仓室。阶段包括从空结构生存搭建、保留部分现有方块后补建、首个公开线圈 / 玻璃子信道升级、拆除普通结构并保留控制器与 TileEntity。没有暴露线圈 / 玻璃的机器不强造替换场景。

所有操作经正式 `BuildService` 队列执行，以真实世界 tick、库存扣料和掉落物核对。阶段上限 240 tick，每轮原生放置预算 200，扫描时间预算 5000 ms，位置上限 100000。这比正常发布默认值更宽松，用于功能排查，不代表默认配置性能测试。扫描无法中断卡在原机内部的一次调用。

原生搭建器返回完成、原机 `checkStructure` 成型、补建后成型、替换后成型及逐物品材料账分别记录。成型不表示已通电运行配方。只读扫描记录的是空闲控制器状态快照；运行中配方、真实客户端发包、GUI 与 AE 网络取料未包含在此全量测试中。

测试持有真实仓室方块，原生搭建器仍决定数量、位置和朝向；有限的 35 个材料槽、基础仓室等级、未配置专用通道、特殊催化物和测试世界环境都可能限制结果。这些情况保留原始原因，不计为全面通过。

完成后生成可读矩阵：

```sh
python3 tools/summarize_pack_validation.py /server/machine-validation verification/full-pack \
  --gt-jar /server/mods/gregtech-5.09.54.205.jar
```

正常发布前重新执行不带 `terminalPackTests` 的 `build`。`verifyDistribution` 会拒绝包含测试夹具或仍带开发名称的正式 JAR。
