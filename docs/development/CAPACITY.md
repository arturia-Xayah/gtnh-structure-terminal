# BigInteger 容量与性能 / Capacity and performance

0.2.4 将总容量、已用字节摘要、主机 NBT、掉落与网络摘要中的容量值改为 BigInteger。分数用量原本已经使用 BigInteger；本次同时将总容量对应的 1/2048 字节预算缓存，只有加载或扩容才重新乘算。

## 数据边界

- 总容量不再以 signed long 或 8 EiB 为上限。BigInteger 编码采用最多 4096 字节的安全长度检查，允许至多 32767 位正数，防止损坏长度触发异常内存分配。
- 每资源数量仍为 AE2 原生 signed long。不能把总容量扩大误认为同种物品数量也能越过 Long.MAX_VALUE；超限存入拒收，元件迁移保留来源。
- 类型计数、Java 集合长度仍为 int，实际规模受内存、磁盘和 AE 枚举成本约束。
- 容量以二进制单位显示到 RiB，超过范围后用三位有效数字的指数表示，避免 GUI 长文本。
- AVS2 与旧 MaximumBytes NBT 自动读取；新的快照使用 AVS3，不能直接交给旧版本。回滚需要完整升级前备份。

[外部格式、混合日志与恢复](STORAGE_FORMAT.md)

## 可重复基准

```sh
python3 tools/benchmark_vault_capacity.py \
  --java /path/to/java25/bin/java --output /path/to/capacity-benchmark.json
```

基线固定为 0.2.3 提交 bf87456570ffc208be29386b5a7fab4ff7a07024。程序从 Git 取出旧 Resource/VaultStore 源码，与当前核心分别编译到独立临时目录，不启动 Minecraft。Java 25，256 MiB 堆；查询先预热，取 7 组中位数；每组 200000 次模拟插入/取出对。带日志操作取 5 组中位数，每组 5000 对实际存取并 flush。每个对包含两次操作，表中按单次操作计算。

本机本轮结果：

| 版本与容量位数 | 模拟查询 ns/次 | 带日志存取 µs/次 |
|---|---:|---:|
| 旧版，63 位 | 14.51 | 5.456 |
| 新版，63 位 | 11.27 | 5.550 |
| 新版，201 位 | 11.01 | 5.280 |
| 新版，1025 位 | 37.33 | 5.599 |

该微基准为单一类型、无争用、热 JVM 的账本开销，允许 JIT 优化；不包含网络路由、全库遍历、客户端绘制或整包 TPS。表中小幅升降不应当作确定性能提升。大整数极端变长会增加算术成本，但本轮实际日志存取差异很小；磁盘调用成本占主导。真实 AE 后台套件结果另见 [整合验证](VALIDATION.md)。

[原始数据](../../verification/modernization-0.2.4/capacity-benchmark.json)

## 验证范围

单元测试覆盖：跨过 Long.MAX_VALUE 的容量和已用字节、满容量物品/流体精确分数、巨大容量日志重放和快照、旧 AVS2 快照与 op=2 混合日志升级、重复回执、坏长度和负容量拒绝。

后台验证覆盖：旧主机 NBT、160 位扩容、真实描述包 wire 编码、损坏 NBT 锁定，以及新进程恢复。另从 0.2.3 的实际后台测试世界副本升级并再次重启，原世界不修改。未调用服务端被剥离的客户端专用数据包 getter；未启动客户端。

## English

Total and used bytes now use BigInteger end to end. Native per-resource counts remain long, type counts remain int, and encoded capacity values are bounded to 4096 bytes for safe parsing. Cached capacity units avoid repeated multiplication on inserts. AVS2/legacy NBT migrate to AVS3. The isolated warmed single-type benchmark above is not a game TPS benchmark; journaled operations stayed around 5.3–5.6 µs per operation in this run. Complete stopped-world backups are required for version rollback.
