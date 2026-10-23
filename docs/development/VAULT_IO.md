# 存储端口、优先级与主动输出 / Storage port internals

目标 GTNH 2.9.0-RC-2，AE2 rv3-beta-1080-GTNH。玩家操作见 [存储指南](../features/STORAGE.md)，实际结果见 [整合验证](VALIDATION.md)。

## 原生路由

`TileAccess` 实现 `IPriorityHost`，直接打开 `GuiBridge.GUI_PRIORITY`，复用原版界面、表达式与容器。优先级为 signed int，保存在仓室 NBT，修改后通知 AE 存储阵列更新。`VaultInventory` 返回此优先级与 READ_WRITE Reshuffle 权限；不另建重排算法。

## 独占输出模式

`TileOutput extends TileAccess`，复用结构成员和所有权判断；结构要求恰好一个端口。`getCellArray` 永远为空，导入元件不向目标网伪报库存变化。目标网只能看到已接受的输出，避免将来源挂载成目的地。

转移通过 `Platform.poweredInsert` 模拟后执行，保留 AE 能源、安全与路由逻辑。先从来源账本预留，目标拒收的常规余量退回。普通跨 Mod 搬运不是分布式事务，进程中断或第三方插入异常仍需完整世界备份恢复。

## 筛选与交互

36 个 `StorageName.CONFIG` 原生虚拟槽同时支持物品和流体，客户端继承 `AEBaseGui` 的 NEI 拖入路径。服务器校验槽位、数量、距离和 BUILD 权限。3 个真实升级槽分别支持模糊、反相、矿物词典筛选卡。复用原生 `findPrecise/findFuzzy`、FuzzyMode 与 OreFilteredList；非空词典表达式优先于槽标记。无标记及无词典表达式时输出全部，包括装反相卡的情况。

正常挖取端口保留配置、标记与真实升级卡。原生优先级使用 WORLD_NBT TileEvent，避免覆盖 AEBaseTile 的 final NBT 入口。

## 调度边界

- 精确白名单编译为至多 36 个账本键，直接查找并轮询，不扫描无关库存。
- 其他筛选使用 `VaultStore` 的有序轮转索引，不每 tick 复制/排序全库存；索引不单独存盘，加载时从库存条目重建。
- 每次至多检查 32 类型，每类型至多 2^30 个物品或 mB。软时间预算 0.5 ms，至少尝试一个类型；单次 AE 调用可能超过预算，因此不是严格 tick 时间保证。
- 成功后下一 tick 继续；可输出类型被拒收时从 2 tick 指数退避至 20 tick。无供电/频道时等待 20 tick。新元件导入或筛选修改会唤醒。
- 解码 LRU 缓存上限 256 个类型。没有整库异步扫描线程。

## 容量与格式

总容量、已用字节及分数计量使用 BigInteger，容量对应的 1/2048 字节单位预算在扩容/加载时缓存。每资源数量仍是 AE2 原生 signed long；类型计数为 int。ByteFormat 使用 1024 进位、三位有效数字，超过命名单位范围后使用指数形式。外部快照升级为 AVS3，支持读取 AVS2、旧 op=2 和混合日志；细节见 [格式说明](STORAGE_FORMAT.md)。数值编码最多 4096 字节用于损坏数据的分配保护，不再以 long 限制总容量。

## 验证边界

新增 `vault_io` 专用后台套件使用真实 AE 控制器、驱动器、物品/流体元件、原生优先级容器、ReshuffleTask 及虚拟槽数据包。独立第二进程验证保存恢复。未启动客户端，NEI 实际鼠标拖入、图形显示和资源包仍需客户端验收。网页全息界面为交互设计稿；本版未将其动画整体移入游戏。

## English

The link uses native AE2 priority and reshuffle contracts. The exclusive output port never mounts private stock; it injects with native poweredInsert and bounded, fair batches. Precise whitelists directly address ledger keys; broad filters rotate a live-key index. Full/offline networks back off. Native phantom slots and fuzzy/inverter/ore cards retain server-side validation. Total capacity is now BigInteger with cached accounting units. Native per-type counts remain long. AVS2/legacy NBT migrate into AVS3; upgraded files are not backward compatible. Dedicated-server evidence does not replace client drag/drop or visual acceptance.

## 目标网络选择性接受

拒收不会重置轮转游标，有限且稳定的库存会继续扫描到接受的类型；并非一次拒收就停在同一项。广泛筛选最多 32 类型/调用，且受 0.5 ms 软预算及 2～20 tick 退避影响，发现稀有可接受类型可能较慢。精确白名单直接查键。专项测试使用原生分区元件，只允许绿宝石，在另外 1000 个被拒收的 NBT 类型后找到目标；测试推进调度用世界总 tick 以覆盖退避，不将其报告为实时延迟测试。
