# 聚合器读取缓存配置 / Storage read cache configuration

[中文首页](../../README.md) · [English README](../../README.en.md) · [存储器使用说明](STORAGE.md)

此功能自 **0.3.0-alpha** 起包含在整合 Mod 中。

首次启动后自动生成 `config/gtnh-modernization.cfg`，修改其中的 **`storagecache`** 分类。专用服务器以服务器文件为准，客户端的值不会覆盖服务器；单人游戏使用本地文件。修改后重启**整个游戏或服务端进程**，退出并重进单人存档不等于重新加载 Mod 配置。

[可直接参考的默认配置](../../examples/config/gtnh-modernization.cfg)

| 配置项 | 默认值 | 作用范围 |
|---|---:|---|
| `keyMaxEntries` | 4096 | 每个存储链接仓、每个通道的身份键 LRU 条数 |
| `keyMaxBytes` | 16777216（16 MiB） | 对应身份键缓存的计费字节预算 |
| `prototypeMaxEntries` | 16384 | 每个存储链接仓、每个通道可保留的解码原型数 |
| `prototypeMaxBytes` | 67108864（64 MiB） | 对应原型缓存的计费字节预算 |
| `outputMaxEntries` | 256 | 每个主动输出总线的原型 LRU 条数，物品/流体合计 |
| `outputMaxBytes` | 8388608（8 MiB） | 对应输出总线缓存的计费字节预算 |
| `maxEntryBytes` | 1048576（1 MiB） | 单条缓存允许的最大计费权重，适用于上述全部缓存 |
| `globalMaxBytes` | 268435456（256 MiB） | 所有聚合器的键、原型及输出缓存共同使用的总预算 |

物品与流体是两个通道。例如一台存储链接仓的键缓存最多各保留 4096 条，但两者都还受同一个全局预算约束。总预算是共同上限，不是给每台机器预留同等额度。

填写非负十进制整数；条数最大 1000000，字节最大 9223372036854775807。负数、无法解析的值或超过范围的值会在启动时报告并恢复该项默认值。配置分类名使用上面的全小写 `storagecache`，保留文件中的 `S:` 前缀。

## 关闭或减少缓存

- 将 `globalMaxBytes` 设为 `0`，关闭这些键/原型缓存。
- 将某类的条数或字节预算设为 `0`，只关闭该类缓存。
- 将 `maxEntryBytes` 设为 `0`，拒绝所有缓存条目。
- 超出单条上限的资源仍能存取和显示，只是每次按需解码。

预算不足时，身份键及输出原型采用 LRU 淘汰。完整库存扫描只利用已有原型和空余预算，不会为了整次扫描反复挤掉所有已缓存原型。任何缓存关闭、拒绝或淘汰都**不会删除物品、减少容量、改变过滤/优先级或省略库存类型**。

## 字节预算的含义

它是可重复计算、严格记账的**缓存权重预算**，并非 JVM 实际堆内存硬上限。令 `S` 为该资源的 canonical key 与 payload 两个编码数组的合计长度：

- 身份键条目的计费权重：`2 × S + 128` 字节。
- 解码原型与输出条目的计费权重：`2 × S + 512` 字节。

这为对象及共享标签引用留出估算开销；不同 JVM、NBT 结构和共享程度的实际内存不会与权重完全相等。库存账本、读取视图索引/数量数组、AE 返回列表、AE 自身的全局共享 NBT，以及正在执行操作的临时对象不受此缓存预算限制。关闭缓存也不会把实际库存从内存中卸载。

提高预算通常能缓存更多资源，降低预算可能增加解码开销。大 NBT 场景还要考虑一次完整列表的临时分配；不能仅根据此配置保证任意基地的 MSPT。

## English

**0.3.0-alpha** generates `config/gtnh-modernization.cfg`. Edit the lowercase `storagecache` category and restart the entire game/server process. Dedicated-server settings are authoritative; client values do not override them.

Key/prototype entry and byte limits apply separately to each item/fluid adapter. Export-bus limits combine both channels. `globalMaxBytes` is shared across all three cache kinds and all vaults. Zero disables the corresponding retention; zero global budget disables all listed caches. Inventory remains complete and readable, with uncached decoding as needed.

Values are non-negative decimal integers. Entry limits are capped at 1000000; byte limits accept signed-long non-negative values. Invalid entries are logged and reset to defaults. Byte values are accounting weights, not a JVM heap cap: encoded keys cost `2*(key bytes+payload bytes)+128`; decoded prototypes cost `2*(key bytes+payload bytes)+512`. Ledger/index/list memory, AE shared NBT and transient operations are outside this budget. Full scans only admit prototypes with available room, avoiding whole-working-set eviction on each pass.
