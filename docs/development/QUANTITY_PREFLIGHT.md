> 本文记录早期 AVS4 数量实现的实测范围；当前元件统计开发版使用 AVS5，见 [当前格式](STORAGE_FORMAT.md) 与 [当前验证](VALIDATION.md)。这些旧性能数据不代表新构建的重新测量。

# BigInteger 库存数量：先行测量与实现

2026-11-02，GTNH 2.9.0 正式版，Java 25.0.4.1，macOS ARM64。先对照 0.2.6 的 long 账本与隔离的 BigInteger 原型测量，再实现 0.3.0。所有测量均未启动客户端。

## 范围

总容量和分数用量原本就是 BigInteger。本轮评估改变的是每种资源的数量。常规 IO 仍接收 AE 原生 long 请求，日志仍用 long 记录单次增减；完整数量和全精度导入记录使用 BigInteger。没有按每个实际物品分配对象，代价随类型数、更新次数和数字位数增长。

独立账本矩阵：1 / 1000 / 100000 类型；40 / 127 / 255 / 1023 位数量。每格 3 个独立 JVM，顺序交替，共 45 次原型/基线进程。每进程含预热、多组模拟/实际存取、14,000 条增减日志、枚举、强制检查点和重新加载。堆为 512 MiB。40 位数量可与 long 版做相同数据的比较；大数量需要更大的总容量，因此包含已有容量大整数算术的位宽变化。128/1024 级数据不等于只测一个加法指令。

实现后另运行 18 个生产账本进程（两种类型规模 × 三种位宽 × 三个独立 JVM），复查代码移植和编码边界检查的成本。原型与最终生产结果分别保留，不能混称同一版本。

## 后台服务器计时

真实专用服务器，1–6 GiB 堆；同一进程交错并反向运行 long / BigInteger，1000 与 10000 类型，每种普通/超大数量两轮。共 16 个阶段，每阶段 60 tick 预热、120 tick 测量，合计 **1920 个有效 tick**。

每个有效 tick 执行 2000 次模拟存取、200 次实际变更和一次日志 flush，每 20 tick 做一次全库物品/流体原生编解码与列表构建。使用 999/9999 种真实 NBT 物品加一种水；原生 AE 不把任意流体 NBT 标记当作不同类型，不能人为把它们算作独立类型。

计时由仅在测试包中的 ASM 探针覆盖 `MinecraftServer.tick` 的入口和返回，排除 tick 之间的休眠，包含 Forge END 中的负载。每个样本断言整个 tick 时长不小于负载时长。直接读取原生 `tickTimeArray` 会漏掉这里的 END 负载，相关试跑不计入结果。检查点在测量区间外单独计时。

**此项测的是完整整包后台进程内的账本/原生列表压力负载，没有连接真实 AE 路由网络或客户端，也没有同时运行玩家工厂。** 不能将结果直接当作任意基地的 TPS 保证；真实 AE 网络的功能验证另见当前 [验证记录](VALIDATION.md)。服务器同进程两轮不是多机器性能统计，GC、JIT、系统调度和存盘都会带来长尾。

| 类型数 / 计数 | 两轮 MSPT 中位数的中位值 | 负载耗时中位值 |
|---|---:|---:|
| 1000 / long，40 位 | 6.36 ms | 5.32 ms |
| 1000 / BigInteger，40 位 | 6.21 ms | 5.35 ms |
| 1000 / BigInteger，127 位 | 6.16 ms | 5.39 ms |
| 1000 / BigInteger，1023 位 | 7.55 ms | 6.33 ms |
| 10000 / long，40 位 | 6.82 ms | 5.95 ms |
| 10000 / BigInteger，40 位 | 6.97 ms | 6.19 ms |
| 10000 / BigInteger，127 位 | 6.75 ms | 6.05 ms |
| 10000 / BigInteger，1023 位 | 7.18 ms | 6.51 ms |

小幅升降不能据此宣称提速。高位数会提高分配和算术成本。10000 类型的一次原生完整枚举约 20 ms，原来的 long 版本也存在这个成本；不能把“全库遍历变贵”都归咎于大整数。基线还出现过约 443 ms 的整体 tick 长尾，表中的中位数不表示没有停顿，原始 p95/max 全部保留。

## 磁盘与内存

独立矩阵使用 16 字节键、96 字节 payload。100000 类型的快照：

| 数量编码 | 快照大小（约） | 相比 long |
|---|---:|---:|
| long，40 位数量 | 13.95 MB | 基线 |
| BigInteger，40 位数量 | 14.15 MB | +1.4% |
| BigInteger，127 位数量 | 15.15 MB | +8.6% |
| BigInteger，1023 位数量 | 26.35 MB | +88.9% |

实际物品 NBT 长度不同，比例随之改变；这是十进制 MB。日志的普通每次增减仍为固定 long，14,000 条相同增减没有因为库存变大而膨胀。全精度导入和快照需要为数量编码付出长度前缀与实际位数的空间。

原型的 100000 类型保留堆差值约为：long 13.93 MB、40 位 BigInteger 17.93 MB、127 位 18.80 MB、1023 位 29.97 MB。该 JVM 总堆差值估计受类初始化、临时导入 Map 与 GC 活跃引用影响，不是逐对象布局证明；生产移植试跑还记录到更高的瞬时保留堆，不应将这些值作为内存上限。数字越长，数量对象和更新时的临时数组越大；不要在每 tick 格式化全库大整数。

## 结论与实现约束

可采用 BigInteger 作为完整库存数量。普通位宽的新增成本小于这组 IO/列表工作负载的主开销，但不能称为零成本；极端位宽、大量类型、频繁扫描和同步写盘仍会影响 MSPT。

0.3.0 使用 AVS4 全精度快照和导入记录；按需求不迁移 AVS2/AVS3。编码保持最多 4096 字节的读取保护，单类型计数最多 32767 个正数位。常规存取避免每次分配与该极限等宽的临时整数，只有接近极限才做完整边界减法。查询完整数量仅按玩家操作格式化。输出端继续保持扫描预算和批量限制。

AE 可见量为 `min(真实数量, Long.MAX_VALUE)`；在 AE 栈数量正向相加原本会溢出的情况下，使用饱和加法，避免两个聚合器或其他存储相加出现负数。正常可表示的相加和负增量沿用原语义；此保护不要求网络安装下单优化元件。原生 AE 的请求、下单和 UI 仍不具备 BigInteger 总量能力。

## 证据与复现

[原型/基线原始结果](../../verification/quantity-preflight-0.3.0/isolated.json) · [服务器完整结果](../../verification/quantity-preflight-0.3.0/server.json) · [最终账本微基准](../../verification/quantity-preflight-0.3.0/production-ledger.json) · [CSV 汇总](../../verification/quantity-preflight-0.3.0/ledger-summary.csv) · [计时与来源](../../verification/quantity-preflight-0.3.0/provenance.json)

`tools/benchmark_vault_quantities.py --java /path/to/java25 --output /new/path` 重现冻结的原型对照；可加入 `--pack /official/2.9.0/server --fixture /explicit-test.jar` 执行后台负载。测试包用 `-PmodernizationPackTests=true` 构建，普通玩家包不包含探针、原型或测试入口。脚本从 Git 读取冻结的 long 基线，无需旧世界数据。增加 `--production --types 1000 100000 --bits 40 127 1023` 可复查最终生产账本；测试当时的源文件另存于 [原始基准源文件](../../verification/quantity-preflight-0.3.0/benchmark-sources/QuantityBenchmark.java)。

## English

Preflight compares the actual frozen long ledger with a BigInteger prototype, followed by production-ledger checks. Ordinary sizes had comparable server-tick medians; 1023-bit counters increased arithmetic/allocation costs. Full native enumeration and synchronous disk IO remain important. The 1920 valid tick samples use a whole-method server probe and exclude sleep. This is a headless ledger/codec load inside the full pack, not real AE routing, client or factory TPS acceptance. Raw distributions, storage sizes and limitations are linked above. AVS4 uses exact counts and a native long visibility ceiling with positive-overflow saturation.
