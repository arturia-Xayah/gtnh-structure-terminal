> 历史开发构建记录，保留当时的规则、哈希与验证范围；当前发行版见 [最新验证](VALIDATION.md)。

# 0.3.0 · BigInteger 库存数量验证

日期：2026-11-02。目标 GTNH 2.9.0 正式版、Minecraft 1.7.10，Java 25.0.4.1；所有运行验证均为独立后台服务器，未启动客户端。

先完成 [数量性能预评估](QUANTITY_PREFLIGHT.md)，再将每种物品/流体的数量改为 BigInteger。原型对照与最终代码的功能测试分别记录，不能把预评估当成最终 JAR 的完整 TPS 基准。

## 本轮结果

| 范围 | 结果 |
|---|---|
| 单元测试 | **99 项通过** |
| 超大数量与双聚合器 AE / 进程重启 | **6 / 2 项通过** |
| 优先级、Reshuffle、输出 / 重启 | **14 / 2 项通过** |
| 原有仓库存取 / 重启 | **38 / 4 项通过** |
| 锻炉功率 | **26 项通过** |
| 普通与压缩聚变 | **126 项通过** |
| AE 下单、批量发配与原料标记 | **51 项通过** |
| 结构终端 | **3 台样本主机、10 个操作阶段通过** |
| 正式构建 | **148 个 Java 8 生产类，与执行夹具逐字节一致** |

本轮共 **269 项模块后台检查**，另有 10 个结构操作阶段。没有执行旧格式世界迁移，也没有沿用此前旧世界升级的结果。

新增单元测试覆盖：多次原生 long 存取跨过 long 上限、精确物品/流体计费、日志和快照恢复、1023 位导入与重复回执、超容量导入拒绝、32767 位数量边界，以及十万组有符号加法对照。AVS2/AVS3 明确拒绝读取，按要求不提供旧版本库存迁移。

新增真实后台场景：两台合法成型的仓库，连接同一个供电 AE 网络，各自持有约 256 位的物品和流体数量。检查完整库存、两仓汇总、可见数量饱和、原生模拟/实际存取守恒、正向溢出保护、按需精确聊天查询，以及第二个进程的库存/UUID/AE 网络恢复。这里检查的是实际网络功能，不是 preflight 的独立列表负载。

原生 AE 的数量仍是 long；本 Mod 的正向溢出保护仅改变原本会溢出的正数相加，正常范围及负增量语义不变。它随存储模块加载，不依赖下单优化元件。未安装优化元件的网络仍使用原版下单计算与发配；这不意味着其原本会溢出的库存汇总保持错误结果。

## 数据与发布范围

- [生产 JAR 与全部验证清单](../../verification/modernization-0.3.0/manifest.json)
- [超大数量与原生 AE](../../verification/modernization-0.3.0/big_quantity/big-quantity-tests.json)
- [独立进程重启](../../verification/modernization-0.3.0/big_quantity/big-quantity-tests-restart.json)
- [预评估原始数据](../../verification/quantity-preflight-0.3.0/provenance.json) · [方法及范围](QUANTITY_PREFLIGHT.md)
- [0.2.6 正式版适配历史](VALIDATION_0.2.6.md)

普通安装包不包含原型、冻结 long 基线、服务器测试入口或计时探针。所有生产 class 与执行夹具逐字节一致；依赖版本由各套后台直接断言为 GTNH 2.9.0 正式版。新库采用 AVS4，仍需完整世界备份。

客户端 OpenGL、字体、GUI 缩放、鼠标操作没有启动游戏验收。精确聊天查询在服务器侧直接调用并核对完整序列化参数，不能当作客户端排版验收。结构操作仍为三台样本，不代表全部 GTNH 机器逐台验证。

此开发版在本地构建和验证；未经另外发布，GitHub 的既有 0.2.6 下载不会自动变成此版本。

## English

Version 0.3.0 stores exact per-resource quantities in BigInteger with AVS4 snapshots and imports. Native AE uses a capped long view, positive-overflow saturation and bounded transactions; exact quantities can be queried on demand. Preflight and final functional evidence are separate. New tests use two real formed vaults on a powered AE grid and a fresh process restart. No legacy-stock migration or client visual acceptance is claimed. This is a locally validated development build unless separately published.
