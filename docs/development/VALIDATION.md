# 0.2.3 存储端口与整合验证 / Storage port validation

日期：2026-10-22，目标 GTNH 2.9.0-RC-2，Java 25.0.4.1。所有服务器使用 `nogui` 和 `java.awt.headless=true`，未启动游戏客户端。

| 范围 | 结果 |
|---|---|
| 单元测试 | **79 项通过** |
| 存储优先级、Reshuffle 与输出 | **13 项通过** |
| 输出端口独立进程重启 | **2 项通过** |
| 仓库首次进程 / 重启 | **36 / 4 项通过** |
| 锻炉 | **26 项通过** |
| 普通与压缩聚变 | **126 项通过** |
| AE 下单、发配和原料标记 | **51 项通过** |
| 结构终端 | **3 台主机、10 个操作阶段通过** |
| 发行文件 | 136 个 Java 8 生产类，与实际运行夹具逐字节一致 |

共 258 项后台断言，另有 10 个结构操作阶段。每套真实服务器独立世界，仓库与输出均各有第二个 JVM 进程重启；测试夹具不进入正式安装包。

## 新增实际检查

- 原生 ContainerPriority 与有符号 int 优先级保存；高低优先级分别执行真实 AE 注入。
- 原生 ReshuffleTask 对物品和流体按优先级重排，结果数量一致，没有拒收、丢失或增生。
- 36 个混合物品/流体标记、模糊/反相/词典卡与模式 NBT；实际虚拟槽数据包的槽位、数量归一化和距离校验。
- 输出与存储链接仓互斥。输出来源库存不出现在目标网络单元阵列中。
- 百万级物品和流体批量输出；精确筛选在另外 1000 种无关库存中直接定位目标。
- 满盘保留库存并退避；断电、结构失效暂停输出；挖取保留真实卡与配置。
- 跨进程检查 UUID、容量、类型和数量，以及端口筛选、卡与 AE 连接恢复。

本轮累计转移 **2,000,077 个单位**（1,000,000 个铁锭、1,000,000 mB 流体、77 个金锭），共 **3 次实际类型批量转移**。实际调用耗时 p95 **655.875 µs**，最大 **891.084 µs**。只采样实际调用，空闲 tick 不重复计入。该数字包含此环境账本/网络操作，不代表其他硬件、存档或复杂网络的性能保证。0.5 ms 是软预算，单次 AE 调用可能超过它。

调度曾在回归中暴露满盘退避过晚的问题，修正为遇到可输出类型但被拒收时即退避，再通过本轮完整回归。精确白名单直接查找账本，不全库扫描；其他模式采用轮转索引。

## 原有结构与客户端边界

本版保留并重跑 300 种六朝向真实部件布局、部件共用拒绝和恢复、原生 GT 总线占用登记与释放，以及整面材质相关断言。布局范围和客户端渲染边界见 [0.2.2 报告](VALIDATION_0.2.2.md)。

未启动客户端。原生 NEI 鼠标拖入、OpenGL、资源包、GUI 操作和光照未手动验收。后台检查覆盖共享容器/数据包/匹配与服务器行为，不等于客户端交互验收。网页全息动画仍为设计预览，本版主机游戏界面更新了名称与容量格式，未整体移植网页动画。

## 证据

- [发行文件与检查清单](../../verification/modernization-0.2.3/manifest.json)
- [输出/优先级/Reshuffle](../../verification/modernization-0.2.3/vault_io/vault-io-tests.json) / [端口重启](../../verification/modernization-0.2.3/vault_io/vault-io-tests-restart.json)
- [仓库](../../verification/modernization-0.2.3/vault/vault-tests.json) / [重启](../../verification/modernization-0.2.3/vault/vault-tests-restart.json)
- [锻炉](../../verification/modernization-0.2.3/forge/forge-power-tests.json) / [聚变](../../verification/modernization-0.2.3/fusion/fusion-power-tests.json)
- [AE](../../verification/modernization-0.2.3/ae/ae-order-report.json) / [结构操作](../../verification/modernization-0.2.3/structures/machine-validation/report.json)
- [端口调度与 API](VAULT_IO.md) / [整面材质与独占机制](VAULT_SMOOTH.md)

## English

The merged 0.2.3 artifact passed 79 unit tests, 258 dedicated-server checks and 10 structure-operation phases. New cases execute native AE2 priority containers, item/fluid reshuffle, phantom packets and active export. Two independent server restarts cover persistence. The output test transferred 2,000,077 units in three type batches, with p95 655.875 µs and maximum 891.084 µs per observed export call on this machine. These measurements are not a TPS guarantee. Production bytecode matches the executed fixture. No client was launched; browser holographic animations remain a proposal and client rendering/drag-and-drop remain unverified.
