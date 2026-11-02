# 单聚合器查询与枚举优化

本页对应配置功能加入前的 read-optimized 构建。后续 cache-configured 变体已更换缓存管理，设置说明见 [缓存配置](../features/CACHE_CONFIGURATION.md)；本页历史计时不自动代表新变体的性能。
2026-11-02，GTNH 2.9.0 / Java 25.0.4.1。本轮只优化每台聚合器自身的读取，不引入跨机器共享库存或网络级共享索引。

## 实现

- `VaultStore.nativeSnapshot(channel)` 首次访问时建立对应通道索引，随后只在已提交的数量变化时更新对应记录。没有变化时复用不可变快照；数量变化后只重建原生 long 数量数组。类型集合不变时复用资源数组，增加/删除类型时再重建该数组。
- 物品与流体分别维护视图，不再在每次请求中复制全库 HashMap 并扫描另一通道。第一次创建通道索引仍需要扫描账本。
- 每个 AE 适配器缓存当前类型的已解码原型和有序引用。枚举复制原型并写入数量，不重新解析所有 NBT；类型变化时保留仍然存在的原型，移除已删除类型。适配器解绑时清理缓存。
- 原生物品/流体请求使用最多 4096 条的 LRU 身份键缓存；数量不参与键，完整共享 NBT 身份参与键。只对已核对的原生实现启用快捷键，其他实现继续使用完整编码。冷请求、超出缓存工作集的轮询仍需编码。
- 原生 AE 的物品复制共享定义，因此检查原型的共享标签是否被调用方通过 setter 改变；若改变则从原始资源重新恢复，避免污染后续列表。
- 已提交库存变更使视图失效；模拟操作不改变视图。完整数量保持 BigInteger，原生视图只返回饱和 long。同一类型在真实数量变化后仍超过 long 上限时，可见视图继续复用。

账本、日志、容量规则、独占规则和 AE 优先级不变；原有 AE 通知路径继续使用。所有缓存均可从真实账本重建，不写入存档。全量枚举仍需创建/填充 AE 列表，复杂度仍为 O(类型数)。

## 对比方法

优化前的 BigInteger 版 `VaultInventory` 和 `ResourceCodec` 在改动前冻结，测试夹具中的 Baseline 类只更改包名/类名；原始文件及 SHA-256 位于验证目录。两种适配器使用同样的真实成型结构、供电 AE 接口和同一库存账本，分别测量，避免将 long 改 BigInteger 的差别混入本轮结果。

使用四组数据：1000 类型普通 NBT、10000 类型普通 NBT、9000 物品加 1000 种真实流体且数量为 255 位、10000 类型复杂嵌套 NBT。每组在每个独立后台进程中交错执行两种适配器，进程间反转先后顺序。

每个阶段先测清空派生缓存后的第一次枚举，再预热 80 tick、测量 120 tick。每个测量 tick 执行 128 次常用类型查询、128 次流式轮询查询、一次不变库存枚举、16 次有日志的数量变更和一次变化后枚举。每次完整枚举均请求物品和流体两通道，并新建原生 AE 列表。数量变更成对恢复，最后逐类型核对守恒。

计时包含真实结构可用性检查、身份键处理和适配器查询；计时操作直接调用适配器，不将其称为完整 AE 网络路由延迟。真实 AE monitor 的物品/流体列表正确性另行断言。完整服务器 tick 由测试专用入口/返回探针计时，排除 tick 间休眠，并断言覆盖全部测试负载。

记录耗时 p50/p95/max、主线程分配字节、清缓存后的第一次枚举和 GC 后保留堆差值。保留堆差值来自整个服务器，受 JIT、原生全局缓存和 GC 影响，允许负值，不是逐对象布局证明或内存上限。首次读取为原生全局类型已预热、聚合器派生缓存为空的场景，不等同于整包冷启动。

## 结果

三个独立后台进程，24 个阶段、2880 个有效 tick，全部守恒/列表断言通过。下表是各进程阶段中位数的中位值；数据集与此前约 20 ms 的数量预评估不同，不直接使用此前数值作分母。

| 类型与数据 | 原完整枚举 | 优化后 | 比值 | 库存变化后原枚举 | 变化后优化 |
|---|---:|---:|---:|---:|---:|
| 1000，简单 NBT | 2.033 ms | 0.181 ms | 11.2× | 1.526 ms | 0.174 ms |
| 10000，简单 NBT | 9.324 ms | 1.499 ms | 6.2× | 8.723 ms | 1.320 ms |
| 10000，物品/流体混合，255 位数量 | 8.637 ms | 1.625 ms | 5.3× | 8.147 ms | 1.365 ms |
| 10000，复杂嵌套 NBT | 89.731 ms | 0.448 ms | 200.1× | 88.328 ms | 0.451 ms |

这些倍数仅描述所测数据；普通与复杂 NBT 阶段的 JIT/GC 状态不同，不能据此推断“复杂 NBT 必然比普通 NBT 枚举更快”。热枚举均跳过解码，原始 p95/max 和各进程中位值保留在数据中。

普通 10000 类型的单次枚举主线程分配由 12.355 MB 降为 1.104 MB，复杂 NBT 由 46.591 MB 降为 1.104 MB。代价是缓存保留堆估计分别约 3.767 / 12.877 MB。普通 1000 类型估计 0.554 MB，但其中一轮为负值，说明服务器堆差值估计有噪声。

第一次枚举仍需建索引和解码：普通 10000 类型从 4.668 ms 变为 11.582 ms，复杂 NBT 从 67.054 ms 变为 91.850 ms；本方案改善后续重复读取，不能称首次读取也更快。

**阶段负载中的精确查询没有普遍提速。** 普通 10000 类型的常用查询批量均摊由 6.554 μs 变为 18.831 μs，混合库存由 6.351 μs 变为 20.229 μs；复杂 NBT 常用查询由 9.608 μs 降为 4.417 μs，超过缓存工作集的轮询由 9.481 μs 变为 9.215 μs。保留这些回退结果，不将身份键缓存的局部收益直接当作整条 AE 查询收益。额外交错查询与阶段归因实验见下节。

[全部汇总](../../verification/vault-read-optimization/summary.md) · [原始汇总 CSV](../../verification/vault-read-optimization/comparison.csv) · [JSON](../../verification/vault-read-optimization/aggregate.json) · [第一轮](../../verification/vault-read-optimization/round-0.json) · [反序轮](../../verification/vault-read-optimization/round-1.json) · [第三轮](../../verification/vault-read-optimization/round-2.json)


## 交错常用查询与耗时分解

一个另外的后台进程，在同一台机器上交错先后顺序，每组预热 20000 次/适配器，120 组 × 128 次调用。仅测常用命中集，不能代替超过缓存容量的流式访问或整网路由。各值为批量中位数除以 128。

| 数据集 | 原查询 μs | 优化查询 μs | 可用性检查 μs | 原编码 μs | 缓存编码 μs | 已有键账本查询 μs |
|---|---:|---:|---:|---:|---:|---:|
| 1000_simple | 4.750 | 4.024 | 3.955 | 0.724 | 0.020 | 0.016 |
| 10000_simple | 4.397 | 3.732 | 3.696 | 0.678 | 0.016 | 0.007 |
| 10000_mixed_huge | 4.923 | 4.181 | 4.037 | 0.739 | 0.021 | 0.009 |
| 10000_rich_nbt | 14.222 | 5.091 | 4.864 | 5.454 | 0.072 | 0.030 |

交错常用查询中缓存有收益，普通 10000 类型约快 15%，复杂 NBT 约快 64%。可用性检查仍检查真实结构，没有通过省略机器状态检查换取速度。分段独立测量，不能精确相加成端到端时间。

与阶段混合负载的结果不同，说明不能将缓存命中微基准当作所有负载的保证。阶段切换时的 JIT、GC 与负载状态可能参与波动；尚未逐项定因，阶段测试中出现的查询回退保留，不能据本结果抹去。

[配对查询原始数据](../../verification/vault-read-optimization/paired-query.json) · [进程与夹具来源](../../verification/vault-read-optimization/paired-summary.json)

## 功能回归与安装包

102 项单元测试及 66 项存储后台检查全部通过，后者包含超大数量双仓 AE 汇总、优先级、Reshuffle、输出过滤/公平扫描、容量/迁移/独占结构及三组独立进程重启。读取专用夹具另外检查原型被调用方修改、类型增删、模拟不变、键缓存上限和四台真实供电 AE monitor 的结果。

生产包共 156 个 Java 8 class，与三轮枚举夹具、交错查询夹具及本轮功能回归夹具逐字节一致。相对初次大整数开发版，只有仓储模块的生产 class 变化；其他功能沿用当时验证，没有把它们计为本轮全部重跑。

[本轮验证清单](../../verification/vault-read-optimization/manifest.json) · [当前验证说明](VALIDATION.md) · 历史本地构建 `read-optimized`（未作为发行附件上传）

本轮为本地 read-optimized 开发变体，Mod 内部版本仍为 0.3.0-alpha，未推送或发布到 GitHub。初次大整数版本的 JAR 和性能/功能证据保留在原目录，不覆盖历史验证文件。

## 复现

使用 `-PmodernizationPackTests=true` 构建测试夹具，执行 `tools/benchmark_vault_reads.py --java /path/to/java25 --pack /official/2.9.0/server --fixture /test.jar --output /new/output --trials 3`。仅启动隔离专用服务器；生产包不包含冻结基线、测试入口或探针。交错查询另用 `tools/benchmark_vault_lookup.py`，参数与上述相同，但没有 `--trials`。

## English

Single-vault derived per-channel snapshots, cached decoded prototypes and a bounded native-identity encoding cache reduce repeated work. Persistent stock remains authoritative and independent. Full listing remains linear in returned types. Measurements use frozen pre-change adapters, native AE lists and real powered formed vaults; direct-adapter timings include availability checks but are not whole-network latency. Cold caches, churn, allocation and retained-heap estimates are reported separately. No game client is launched.
