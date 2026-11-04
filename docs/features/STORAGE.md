# ME元件聚合存储器 / ME Cell Aggregation Storage

[中文首页](../../README.md) · [English README](../../README.en.md)

## 建造

在 NEI 搜索 **ME元件聚合存储器**、**ME存储链接仓**、**ME元件聚合存储器输出总线**，使用 NEI 查看配方。搭建 3×3×3 空心立方体：

- 主机在任意一个面的中央，朝外；支持六个方向。放置时平视为水平朝向，俯视或仰视超过 60° 为顶面或底面朝向。
- 其余外壳位置放 23 个 LuV 机器外壳、1 个标准 GT 输入总线、1 个 ME 存储链接仓（或输出总线）。总线与接口可放在顶面、底面、棱边与角落。总线与接口均不可被多台仓库共用，共用时相关仓库均无法成型，解除冲突后恢复。已登记的原生 GT 多方块占用的总线也不可兼用。
- 中央为空气，不放存储核心。

ME 存储链接仓可替换为 **一个 ME 主动输出总线**。两种 ME 端口互斥，同一结构只能安装其中一个；输入总线仍需保留。

可额外将一个 LuV 外壳替换为 **一个普通 GT 物品输出总线**，用于接收退回的容量元件，外壳因此减为 22 个。它是独立的物品出口，不替代上述 ME 端口；只接受标准 GT 输出总线，不接受 ME 虚拟输出总线。所有总线均不可与其他仓库或已登记的 GT 多方块共用。

从上方观察每个高度层，每幅图第一行为正面：

```text
顶层       中层       底层
CCC        IHC        CCC
CCC        A.C        CCC
CCC        CCC        CCC

C 外壳   H 主机   I 输入总线   A 访问接口   . 空气
```

将访问接口连接到供电的 AE 网络：1 个频道，2 AE/t。当前只接受标准 GT 输入总线，不接受 ME 虚拟输入总线。支持结构终端自动搭建和 NEI 多方块预览；预览尺寸 1／2／3 对应链接仓、主动输出、带退回总线三种示例。

## LuV 制作与统一外观

主机：LuV 机器机壳（Hull） ×1、LuV 电路 ×4、LuV 传感器 ×2、ME 驱动器 ×1，熔融焊锡合金 576 L，LuV 组装机加工 30 秒。

ME 访问接口：LuV 机器机壳（Hull） ×1、LuV 电路 ×2、LuV 发射器 ×2、ME 驱动器 ×1，熔融焊锡合金 288 L，LuV 组装机加工 20 秒。配方消耗 30,720 EU/t，不保留低等级工作台配方。

输出总线配方将上述接口配方中的 ME 驱动器换为 ME 输出总线，其余材料和加工参数相同。

未成型时使用原生 LuV 外壳及各功能面板。成型后整个结构统一采用平滑的蓝白紫外壳，每个完整面共用一张连续贴图，方块之间不再重复边框；主机保留原生屏幕、ME 链接仓保留原生 ME 输入标识、主动输出保留输出标识。支持材质包覆盖。外观不改写方块、总线等级、染色或材质 NBT；结构失效后恢复原生显示。

已移除废弃的自定义外壳注册。升级时旧式外壳方块及物品不再保留；请使用原生 LuV 机器外壳，主机与仓库库存不受此移除影响。0.3.0 的库存格式与 0.2.x 不兼容，升级处理见下方“搬迁、备份与恢复”。

## 扩容与迁移

放入元件后，每秒自动检查并尝试吸收。支持 AE2 的 1k～16384k 存储组件，以及能通过 AE 接口读取容量和内容的物品/流体存储元件；AE2FC 的普通流体元件也可迁移。

普通非空元件先迁移库存和升级卡，之后消耗元件及外壳并增加标称字节容量。安装可选普通输出总线后，可按下方方法退回空元件。升级卡作为普通库存物品保留；元件分区和过滤配置不成为仓库过滤条件。创造、虚空、空间及未知通道元件不吸收，会留在总线中。

0.3.0 起使用以下规则：

| 容量来源 | 每个贡献的字节 | 条件 |
|---|---:|---|
| 数字奇点物品元件 | 17,592,186,044,416 B（16 TiB） | 仅空元件 |
| 人造宇宙物品元件 | 576,460,752,303,423,487 B（512 PiB − 1 B） | 仅空元件 |
| 数字奇点流体元件 | 68,719,476,736 B（64 GiB） | 仅空元件 |
| 人造宇宙流体元件 | 4,503,599,627,370,495 B（4 PiB − 1 B） | 仅空元件 |
| Avaritia Addons 无尽箱子 | 2,147,483,646 B（2 GiB − 2 B） | 仅不带库存/方块 NBT 的空箱 |

数字奇点物品/流体元件使用 **`（原容量 + 1）÷ 32768`**；人造宇宙物品/流体元件贡献完整原始字节容量。四种元件均只接收空库存。不会迁移其中已有的物品或流体，非空时保留整个元件并报告原因；升级卡仍作为普通物品迁移。量子、容器和普通元件保持标称容量。Avaritia 的 `Infinity_Chest` 是胸甲，不属于支持的箱子。

主机点击 **元件明细** 打开分页界面，每页最多六种元件。按物品注册名与型号累计吸收数量，显示原物品图标、实际累计贡献容量以及占本机总容量的比例；悬停显示注册名、数量与字节值。数量与累计字节使用 BigInteger，极长数值使用科学计数法。统计同时保存在主机/掉落物 NBT 和外部事务账本中；重复导入凭据不重复计数，重启或搬迁保留记录。详情只同步给查看者，普通状态广播不携带整张统计表。蓝白详情页提供容量占比条，悬停显示完整名称和注册型号；小窗口采用紧凑布局。页面数据变化时才重新格式化数字和解析图标。贡献记录保存吸收时实际入账的字节，后续修改规则不会追溯重算旧记录。

## 退回容量元件

1. 在结构中额外安装 **一个普通 GT 物品输出总线**，重新检测成型。
2. 打开主机的 **元件明细**，点击要退回的元件行。
3. 输入非负整数数量，超过已吸收数量会自动限额。点击 **全部**填入该类型尚未退回的全部数量。
4. 点击退回。实际数量同时受 **已吸收数量、可释放的空闲容量、输出总线可用空间**限制；不足时只退回能安全放下的部分，并显示实际数量。

退回的是对应型号的**空元件／空组件／空箱**，不会把仓库库存重新装回元件，也不会恢复原分区、过滤或升级卡。库存和已迁移的升级卡仍保留在仓库。已用容量不会因退回而减少，退回后的总容量必须仍容纳全部库存，连不足一字节的流体占用也会计入。明细中的数量、贡献容量及比例随实际退回数量更新；退完某类型后移除该条目。统计记录吸收时实际入账的容量，退回按该类型剩余平均贡献向上取整扣除字节。

普通 GT 输出总线可正常取出或自动输出退回元件。总线已满、锁定为其他物品、结构失效或未安装该总线时，不扣除容量。ME 主动输出总线只导出仓库库存，不能代替这个物理出口。吸收与退回会同步保存维度并等待区块写盘，大存档可能出现一次停顿；点击有短暂限频。

重启后会按保存的退回凭据核对交付。若事务尚未完成，暂时停止仓库存取和吸收；请保留原输出总线及位置，恢复后自动继续，不要换总线或删除外部文件来处理。

右键主机查看状态，可关闭自动吸收后集中放入元件，再开启扩容。

## 主机动画界面

右键主机显示环形容量仪表、最大/已用/剩余字节、类型数量与吸收状态。客户端逐帧绘制扫描线、旋转流光与状态呼吸灯；容量和类型变化在约半秒内平滑过渡。较小的 GUI 尺寸会切换到紧凑布局。

点击 **动画开/关** 可即时切换。本地设置保存在 `config/gtnh-modernization-client.cfg` 的 `client.storageAnimations`，不影响其他玩家、服务器状态或库存同步频率。关闭后数值立即跳到实际值，并停止循环特效。外部编辑配置后重启客户端生效。

悬停容量区查看当前实际值，不经过动画插值；极长数字用六位有效数字的指数形式概括。结构失效或库存锁定时停止装饰动画。检测结构与自动吸收按钮沿用原操作。

## 存储链接仓：优先级与原生 AE 操作

右键链接仓直接打开 **AE2 原生优先级界面**，支持原版的加减按钮、输入和表达式。高优先级先存入，低优先级先取出；同级的已有类型、黏性等规则仍由 AE2 决定。优先级保存在链接仓上，普通采掘和重新放置会保留。

链接仓模式支持 ME 终端、自动合成等原生存取，以及 **Storage Reshuffle**。本机参与原版的物品和流体重新整理、优先级排序及回退流程。

## 输出总线：向 AE 网络主动输出

输出总线连接有电、有空余存储空间的 AE 网络，占一个频道，待机 2 AE/t，搬运额外使用原版 AE 搬运能量。

1. 右键输出总线，打开 **4×9 标记区**。标记只表示类型，不消耗物品，也不指定保持数量。
2. 可从 NEI 拖入物品或流体显示标记。带流体容器可使用 AE2 原生的右键／转换操作选择内部流体。
3. 标记留空时，输出全部物品和流体；有标记时，默认只输出匹配类型。
4. 右侧可插入模糊卡、反相卡、矿物词典筛选卡各一张。模糊按钮使用原生匹配模式；反相卡排除匹配类型。无标记且无词典表达式时，即使有反相卡也输出全部。
5. 安装词典筛选卡后，可打开原生词典筛选页面。非空表达式优先于标记区，并可叠加反相卡；清空表达式恢复标记筛选。

即使未设置标记而目标网络只接受某一种物品，也会轮流尝试并输出接受的类型；被拒收的类型不会堵死后续类型。类型很多时，扫描预算和退避会增加等待。若目的明确，建议设置精确白名单，直接查找目标类型（无模糊/反相模式及非空矿词表达式）。

输出按类型批量处理，并轮流扫描类型；网络装不下、未供电或没有频道时保留库存。主机的自动吸收开关只控制吞入元件；需要暂停输出时，断开网络或取下输出总线。

输出模式下，仓库库存不会直接挂载为网络存储；终端和 Reshuffle 只能访问已经输出到网络的部分。避免同时把同一仓库作为输出来源和输出目的地。普通采掘输出总线会保留筛选、升级卡和模式。

## 读取缓存设置

0.3.0 起可在 `config/gtnh-modernization.cfg` 调整 LRU 条数、局部/全局字节预算与单条准入上限。修改后重启游戏或服务端；预算不改变实际库存或容量。[配置项与默认值](CACHE_CONFIGURATION.md)。

## 容量

| 内容 | 占用 |
|---|---:|
| 8 个物品 | 1 字节 |
| 2048 mB 流体 | 1 字节 |
| 新类型 | 无额外开销 |

物品与流体共用容量。使用精确分数计数，界面的已用字节向上取整。例如 1 字节容纳 1 个物品和 1792 mB 流体。没有人为类型限制，实际规模受服务器内存、磁盘和 AE 列表处理能力限制。

结构损坏、区块未加载或接口无频道/供电时暂停 AE 访问。修复后重新成型可恢复库存。

**0.3.0 起**的总容量、已用字节以及每种物品/流体实际数量均采用 BigInteger，单类型库存可以超过 long 上限。AE 终端仍只显示至多 **9,223,372,036,854,775,807**，超过部分保留在仓库中，取走后继续可用；单次 AE 请求仍受原版 long 接口限制。多个存储汇总时，正向数量溢出会饱和到这个上限，避免变为负数；这个保护不要求安装下单优化元件。

手持要查询的物品，**潜行右键主机**可在聊天栏查看完整精确数量及 AE 可见量；持流体容器时也会查询其中流体。查询不消耗物品，没有周期性全库大数字同步。普通右键仍打开主机界面。

类型计数仍为 int，实际规模受内存、磁盘和 AE 枚举能力限制。容量与单类型数量的编码采用最多 4096 字节的安全长度检查，可表示至多 32767 位正数；接近极限时拒收多余数量。具体性能范围见 [数量预评估](../development/QUANTITY_PREFLIGHT.md)。

界面按 1024 进位，支持 `B / KiB / MiB / GiB / TiB / PiB / EiB / ZiB / YiB / RiB`。超过 RiB 命名范围时使用带指数的紧凑形式，例如 `1.3 ×10^33 RiB`，避免长数字挤出界面。

## 搬迁、备份与恢复

0.3.0 使用新的 AVS6 库存格式，可读取此前本地开发版 AVS5，检查点时升级；不迁移 0.2.x 的 AVS2/AVS3/AVS4 文件。已有 0.2.x 聚合器需先在旧版导出库存，升级后新建仓库并重新准备容量来源。不要直接接管旧外部文件。

普通采掘主机得到的物品保留库存 UUID 和容量；重新放置并成型后访问同一库存。复制主机物品不会复制库存，同一 UUID 不允许两个主机同时挂载。创造模式无掉落拆除不提供主机物品，请保留备份。

实际文件：

```text
<世界目录>/data/aestoragevault/<UUID>.snapshot
<世界目录>/data/aestoragevault/<UUID>.wal
```

停服后整体备份世界，包括两个文件、区块与玩家数据。不能只备份主机 NBT，不能单独恢复旧库存文件。文件缺失、损坏或物品注册缺失时，仓库会锁定并报告，避免用空库存覆盖。遇到锁定时保留文件和日志，从完整备份恢复。

元件迁移回执用于防止恢复来源后再次加容量；普通 AE 搬运与其他容器并非跨 Mod 的统一原子事务，断电或部分回滚仍可能造成不一致。移除整合 Mod 后，机器与外部库存无法访问；恢复对应版本和完整世界后再使用。

## English

The controller is named **ME Cell Aggregation Storage**. Its blue-white dashboard animates the ring, sweep, highlights and number changes, with a compact layout for smaller GUI sizes. The animation toggle is client-local (`config/gtnh-modernization-client.cfg`, `client.storageAnimations`), adds no server packets, and snaps to current values when disabled. Hover metrics for non-interpolated current values (very long numbers are summarized). Invalid/locked structures stop decorative motion. Use exactly one Storage Link Hatch **or** Export Bus in the port position. Right-click the link for the native AE2 priority GUI; native item/fluid routing and Storage Reshuffle are supported. Larger priorities insert first, smaller priorities extract first, following AE2's other ordering rules.

The Export Bus instead actively injects into its connected, powered AE grid and never exposes the source stock as network storage. Its 4×9 native phantom slots accept item/fluid NEI markers. Empty filters export all. Supported upgrades: one fuzzy, inverter and ore-filter card each. Non-empty ore expressions override slot markers; an empty expression restores slot filtering. Empty markers plus an empty expression export all, including with an inverter. Transfers use native AE power and security, bounded type batches and fair scanning. Full/offline destinations retain stock. The absorption toggle controls cell imports only. Normal harvesting preserves filters, real upgrade cards and settings.

0.3.0 uses BigInteger for capacity, usage and exact per-resource quantities. AE-visible totals and individual AE requests remain signed longs; positive-overflow additions saturate. Sneak-right-click the controller while holding an item to query exact stock; filled containers also query their fluid. AVS6 reads local-development AVS5, but does not migrate AVS2/AVS3/AVS4 files from 0.2.x. Export old stock before upgrading, then create a new vault and supply new capacity sources. Display units extend to ZiB/YiB/RiB, with compact scientific notation beyond the named range. Type counts use `int` and practical size depends on memory and Java collections.

Unfiltered output eventually reaches destination-accepted types despite other rejected types. Many types and backoff can delay discovery; use a precise whitelist for direct lookup. The 0.3.0 build requires a new inventory when upgrading from 0.2.x; see the quantity preflight for performance and format limits.

Build the hollow 3×3×3 shell shown above: outward-facing controller at the center of any face, 23 native LuV machine casings, one ordinary GT input bus and one Storage Link Hatch or Export Bus, air in the center. The bus and interface may occupy any remaining shell cell, including top/bottom, edges and corners. Neither may be shared between vaults; conflicts prevent both structures from forming and recover after separation. Buses reserved by a registered native GT multiblock are rejected too. No cores. All three custom blocks use LuV assembler recipes (30,720 EU/t), with LuV hulls/circuits and components; there is no workbench fallback. Connect the interface to powered AE: one channel, 2 AE/t. ME virtual buses are excluded. Manual construction is required for this machine.

Forming replaces the whole shell appearance with a smooth blue/white/violet skin. Each complete face shares one continuous texture without repeated block borders. Native screen and ME/pipe port overlays remain visible, with resource-pack support. Controller and connector/port indicators stay visible; native blocks, bus tier, dye and saved texture are never rewritten. Retired custom casing blocks and items are no longer registered or retained on upgrade. Use native LuV machine casings; controller and stored inventory are unaffected by this removal. The inventory-format upgrade limitation above still applies.

Insert supported item/fluid cells or AE2 1k–16384k components. Ordinary filled cells migrate their contents and upgrade cards first, then contribute nominal bytes; housing is consumed. Cards become stored items. Cell partitions/filters are not copied into vault rules. Unsupported, creative, void and spatial sources remain intact. Right-click the controller for status and absorption controls. Each import batch forces a dimension save and may pause the server briefly.

0.3.0 only accepts empty Singularity/Universe cells. Singularity cells credit `(nominal bytes + 1) / 32768`: 16 TiB for item cells and 64 GiB for fluid cells. Universe cells contribute their full nominal bytes: 512 PiB minus one byte for items and 4 PiB minus one byte for fluids. Nonempty high-end cells remain intact; upgrade cards still migrate as ordinary items. The empty Avaritiaddons `InfinityChest` contributes 2,147,483,646 B; chests carrying any NBT other than the import token are retained. Avaritia `Infinity_Chest` is armor, not a supported chest.

Open **Cell details** on the controller for six-row pages of source item icons, absorbed counts, cumulative credited bytes and percentage of total capacity. Counts and credited bytes use BigInteger. Registry item + metadata defines a source type; original cell inventory/filter NBT is not copied into its icon. History is mirrored in controller/drop NBT and committed atomically with the receipt and capacity in the external ledger. Detail pages are sent only to their viewers, split into at most three records per vanilla packet. Full history is never included in periodic summary broadcasts. The blue-white native detail page includes capacity-share bars, complete names in hover details and a compact layout for smaller GUI scales. Number formatting and icon lookup are cached until the page changes. History stores bytes actually credited at absorption time; changing rules does not retroactively recalculate old records.

Optionally replace one of the 23 casings with **one ordinary GT item output bus**. This physical return bus is separate from the required Storage Link Hatch / ME Export Bus choice. It must not be shared with another machine. Open Cell details, click a source row, enter an integer quantity or use **All**, then confirm. All fills the remaining absorbed count; actual delivery is limited by free capacity and physical output room. A partial return reports the delivered count. Returned sources are empty, while stock and migrated upgrade cards stay in the vault. Contribution count and capacity decrease only by the actual return. A full or differently locked output bus makes no debit. The original cell's partition/filter/upgrade configuration is not reconstructed.

Returns use durable debit intents and native-bus delivery receipts for restart recovery. Pending delivery pauses stock IO/imports; retain the original output bus in its original position for recovery. Imports and returns force a dimension save and may briefly pause the server. Complete-world backups remain required.

Items and fluids share an exact budget: 8 items or 2048 mB per byte, no type overhead. There is no artificial type cap, but practical size depends on memory, disk and AE enumeration performance. Broken/unloaded structures or an inactive interface stop access until restored.

Normal harvesting retains the controller UUID; replacement reconnects the same inventory. Duplicate controllers cannot mount one UUID together. External files are `<world>/data/aestoragevault/<UUID>.snapshot` and `.wal`. Always back up and restore the complete stopped world including chunks and players. Missing/corrupt files lock rather than replace stock. Keep files/logs and restore a complete backup. Migration receipts prevent duplicate capacity credit; ordinary cross-container transfers are not a distributed transaction with all other mods. Partial rollback or abrupt power loss can still create inconsistencies.

[开发文档与存储格式 / Development and file format](../development/README.md)
