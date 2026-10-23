# 0.2.4 容量升级与整合验证 / BigInteger validation

日期：2026-10-23，目标 GTNH 2.9.0-RC-2，Java 25.0.4.1。全部服务器使用 nogui 与 java.awt.headless=true，未启动游戏客户端。

| 范围 | 结果 |
|---|---|
| 单元测试 | **85 项通过** |
| 原生优先级、Reshuffle、输出 | **14 项通过** |
| 输出端口独立进程重启 | **2 项通过** |
| 仓库首次进程 / 重启 | **38 / 4 项通过** |
| 锻炉 | **26 项通过** |
| 普通与压缩聚变 | **126 项通过** |
| AE 下单、发配与原料标记 | **51 项通过** |
| 结构终端 | **3 台主机、10 个操作阶段通过** |
| 0.2.3 真实世界副本升级 / 再次重启 | **4 / 4 项通过** |
| 正式包 | 138 个 Java 8 生产类，与后台夹具逐字节一致 |

共 261 项整合后台断言，另有 8 项真实旧世界升级/重启断言和 10 个结构操作阶段。测试夹具不进入玩家安装包。

## 本轮新增检查

- 总容量和已用字节跨过 Long.885.042_VALUE，仍保持物品/流体精确分数预算及满容量拒收。
- 巨大容量的回执、WAL 重放、快照与重启；AVS2 快照、旧 op=2 与新 op=3 混合日志；重复回执与错误长度保护。
- 旧主机 long NBT、160 位扩容、100 位已用摘要的原生 S35 数据包编码/解码，以及坏 BigInteger NBT 锁定。
- 无输出标记、目标网只允许绿宝石时，在另外 1000 种被拒收的 NBT 库存后找到并输出 **12,345 个绿宝石**，其他库存不变。使用真实原生分区元件。此专项通过推进世界总 tick 覆盖调度退避，不代表自然经过的实时延迟；精确白名单直接查找也继续通过。
- 原生 1k 元件无法容纳全部 12,345 个物品，因此专项目的地采用容量足够的 16384k 分区元件。拒收/满盘保留库存另有独立检查。
- 复制上一版实际运行世界，加载原 UUID/容量/1000 余类型与物品/流体数量；停止写出 AVS3 后再启第二个 JVM，仍保持库存及 AE 连接。源世界不修改。

输出套件累计 **2,012,422 个单位、4 次类型批量转移**。自然 tick 收集的调用样本 p95 为 546.75 µs、最大 885.042 µs；不完整采集中间推进 tick 循环的每次调用，不把它作为延迟基准或 TPS 保证。容量独立微基准结果见 [CAPACITY.md](CAPACITY.md)，实际日志存取前后约 5.3～5.6 µs/次。

## 客户端与恢复边界

描述包通过当前整合包自身的编解码器往返校验，不假设原版字段偏移，不调用专用 getter（服务端会剥离客户端方法）。主机 tooltip 的中英资源与正式 ItemBlock 接线经过发行检查；未启动客户端手动验收文字排版、NEI 鼠标拖放、OpenGL、GUI 动画或资源包。

网页全息动画仍是设计稿；本版未整体移入游戏。普通跨 Mod 搬运仍不是分布式事务。升级到 AVS3 后不能直接降级，恢复需完整停服备份。原有连续材质、300 种布局与独占规则继续回归，历史范围见 [0.2.2](VALIDATION_0.2.2.md) 与 [0.2.3](VALIDATION_0.2.3.md)。

## 证据

- [正式文件与检查清单](../../verification/modernization-0.2.4/manifest.json)
- [输出与选择性目标](../../verification/modernization-0.2.4/vault_io/vault-io-tests.json) / [端口重启](../../verification/modernization-0.2.4/vault_io/vault-io-tests-restart.json)
- [仓库与容量](../../verification/modernization-0.2.4/vault/vault-tests.json) / [重启](../../verification/modernization-0.2.4/vault/vault-tests-restart.json)
- [旧世界升级](../../verification/modernization-0.2.4/upgrade/upgrade-1.json) / [再次重启](../../verification/modernization-0.2.4/upgrade/upgrade-2.json) / [文件格式转换](../../verification/modernization-0.2.4/upgrade/formats.json)
- [锻炉](../../verification/modernization-0.2.4/forge/forge-power-tests.json) / [聚变](../../verification/modernization-0.2.4/fusion/fusion-power-tests.json)
- [AE](../../verification/modernization-0.2.4/ae/ae-order-report.json) / [结构操作](../../verification/modernization-0.2.4/structures/machine-validation/report.json)
- [原始容量微基准](../../verification/modernization-0.2.4/capacity-benchmark.json) / [格式](STORAGE_FORMAT.md) / [端口调度](VAULT_IO.md)

## English

The artifact passed 85 unit tests, 261 integrated dedicated-server checks, eight additional upgrade/restart checks against a copied 0.2.3 world, and ten structure-operation phases. BigInteger capacity, legacy files/NBT, native packet round trips and bounded per-resource quantities are verified. Unfiltered output found the only accepted type after 1000 rejected types and moved all 12,345 emeralds without changing other stock. That case advances scheduler ticks and is not a wall-clock latency benchmark. Production bytecode matches the executed fixture; no client was launched. Browser animations remain a proposal and client tooltip/GUI acceptance remains unverified.
