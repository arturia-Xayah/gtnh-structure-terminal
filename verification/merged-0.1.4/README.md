# 0.1.4-alpha 合并验证 / Merge validation

2026-10-21，GTNH **2.9.0-RC-2** 专用服务器、Java25，客户端未启动。AE2 `rv3-beta-1080-GTNH`、AE2FC `1.5.113-gtnh`、StructureLib `1.4.45`、GTNHLib `0.11.52`。

## 本版结果

- 29 项单元测试全部通过：21 项原结构终端测试＋8 项 AE 核心测试。
- 33 项合并版 AE 后台用例全部通过：无元件保持普通/Lite原版路径，元件开关与合网唯一性，GUI服务端设置和NBT，循环/多输出拒绝，候选报告，物品/流体批量发配，拒收退料，多接收器轮转及跨网络订单来源。
- 10,000份单份样板在同一派发tick按4096＋4096＋1808三次成功发送；1,000,000份共245次发送，总量守恒。接收端回送测试产物，不代表GT机器实际加工速度。
- 3台代表控制器（141流体钻机II、1000工业高炉、15750熵变处理厂），共10个适用的搭建、部分补建、线圈替换及拆除阶段全部通过；三台机器搭建、补建后均通过原机成型检查，材料账平衡。高炉替换线圈后也成型。不是336台控制器全量重跑，历史全量结果仍单独保留。
- 正式JAR中75个运行类均为Java8字节码，与AE及结构两个最终测试夹具中的对应类逐字节相同；没有测试入口和内嵌AE2/UniMixins依赖。
- 原结构终端45个逻辑类与0.1.3-alpha逐字节相同；原独立AE优化器27个核心类逐字节相同。两个入口及生成版本类按合并包更新，原有注册ID与蓝白终端贴图保留。

只发布一个JAR，其中保留 `structureterminal` 与 `aeorderoptimizer` 两个FML模块条目，以兼容原有方块身份。升级时移除两个旧的独立JAR。保留ID不是一次真实旧存档升级测试；本轮没有客户端GUI、真实机器运行配方或全量重启恢复测试。

## 数据

[AE原始报告](ae-report.json) · [结构原始报告](structure-report.json) · [逐机CSV](structure-machines.csv) · [结构摘要](structure-summary.json) · [运行类与哈希检查](verification.json)

[AE运行记录](ae-runner.log) · [结构运行记录](structures-runner.log) · [AE日志摘录](ae-excerpt.log) · [结构日志摘录](structures-excerpt.log) · [实际加载Mod列表](loaded-mods.txt) · [构建日志](release-build.log)

[完整构建及后台复测方法](../../docs/development/BUILDING.md#合并版后台验证--merged-integration-suites) · [AE使用说明](../../docs/development/AE_ORDER_INTERNALS.md) · [合并前星门速度测量](../../docs/development/AE_BENCHMARK.md)

离线检查不会启动游戏：

```sh
python3 tools/verify_merged_release.py
```

若自行保存了两组重混淆测试夹具，可加 `--ae-fixture /path/to/ae-tests.jar --structure-fixture /path/to/structure-tests.jar`，额外比较运行类字节码及归档运行记录里的夹具哈希。仅重建的ZIP时间戳可能改变整包哈希，字节码检查与实际运行夹具身份检查分别记录。

## English

29 unit tests, 33 AE dedicated-server functional cases and ten applicable structure-operation phases across three sample controllers pass. All 75 production classes match both tested fixtures byte-for-byte, target Java8 and exclude test code. Existing structure logic and AE core classes remain byte-identical to their baselines. The approved texture and registration IDs are retained; one JAR contains two FML module entries. Remove both old standalone JARs before installing. No game client, real running machine recipe, full 336-controller rerun or existing-save upgrade was tested. Earlier Stargate performance data is clearly separated from merged-build regressions; no successful full Stargate speedup is claimed.
