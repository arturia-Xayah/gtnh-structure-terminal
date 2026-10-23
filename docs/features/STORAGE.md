# ME元件聚合存储器 / ME Cell Aggregation Storage

[中文首页](../../README.md) · [English README](../../README.en.md)

## 建造

在 NEI 搜索 **ME元件聚合存储器**、**ME存储链接仓**、**ME元件聚合存储器输出总线**，使用 NEI 查看配方。搭建 3×3×3 空心立方体：

- 主机在任意一个面的中央，朝外；支持六个方向。放置时平视为水平朝向，俯视或仰视超过 60° 为顶面或底面朝向。
- 其余外壳位置放 23 个 LuV 机器外壳、1 个标准 GT 输入总线、1 个 ME 存储链接仓（或输出总线）。总线与接口可放在顶面、底面、棱边与角落。两者均不可被多台仓库共用，共用时相关仓库均无法成型，解除冲突后恢复。已登记的原生 GT 多方块占用的总线也不可兼用。
- 中央为空气，不放存储核心。

ME 存储链接仓可替换为 **一个输出总线**。两种端口互斥，同一结构只能安装其中一个；输入总线仍需保留。输出总线也不能由多台机器共用。

从上方观察每个高度层，每幅图第一行为正面：

```text
顶层       中层       底层
CCC        IHC        CCC
CCC        A.C        CCC
CCC        CCC        CCC

C 外壳   H 主机   I 输入总线   A 访问接口   . 空气
```

将访问接口连接到供电的 AE 网络：1 个频道，2 AE/t。当前只接受标准 GT 输入总线，不接受 ME 虚拟输入总线。该仓库尚未接入结构终端自动搭建与 NEI 多方块预览。

## LuV 制作与统一外观

主机：LuV 机器机壳（Hull） ×1、LuV 电路 ×4、LuV 传感器 ×2、ME 驱动器 ×1，熔融焊锡合金 576 L，LuV 组装机加工 30 秒。

ME 访问接口：LuV 机器机壳（Hull） ×1、LuV 电路 ×2、LuV 发射器 ×2、ME 驱动器 ×1，熔融焊锡合金 288 L，LuV 组装机加工 20 秒。配方消耗 30,720 EU/t，不保留低等级工作台配方。

输出总线配方将上述接口配方中的 ME 驱动器换为 ME 输出总线，其余材料和加工参数相同。

未成型时使用原生 LuV 外壳及各功能面板。成型后整个结构统一为平滑蓝白紫色科幻面板，九张贴图拼接为每个完整表面。外壳、主机、ME 接口及总线共享连续机身，保留主机图案、接口插口和总线端口标识。外观不改写方块、总线等级、染色或材质 NBT；结构失效后恢复原生显示。

从 0.2.0 或独立版升级：主机 UUID、容量及外部库存不变，将 23 个旧式外壳替换为原生 LuV 机器外壳即可重新成型。旧式外壳保留注册以便已有世界加载，但不再是有效结构材料。

## 扩容与迁移

放入元件后，每秒自动检查并尝试吸收。支持 AE2 的 1k～16384k 存储组件，以及能通过 AE 接口读取容量和内容的物品/流体存储元件；AE2FC 的普通流体元件也可迁移。

非空元件先迁移库存和升级卡，之后永久消耗元件及外壳并增加标称字节容量。升级卡作为普通库存物品保留；元件分区和过滤配置不成为仓库过滤条件。创造、虚空、空间及未知通道元件不吸收，会留在总线中。

右键主机查看状态，可关闭自动吸收后集中放入元件，再开启扩容。每批新元件吸收时会同步保存维度并等待区块写盘，大存档可能出现一次停顿。

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

## 容量

| 内容 | 占用 |
|---|---:|
| 8 个物品 | 1 字节 |
| 2048 mB 流体 | 1 字节 |
| 新类型 | 无额外开销 |

物品与流体共用容量。使用精确分数计数，界面的已用字节向上取整。例如 1 字节容纳 1 个物品和 1792 mB 流体。没有人为类型限制，实际规模受服务器内存、磁盘和 AE 列表处理能力限制。

结构损坏、区块未加载或接口无频道/供电时暂停 AE 访问。修复后重新成型可恢复库存。

总容量和已用字节采用 **BigInteger**，不再受 8 EiB 限制。单种物品数量或流体 mB 数仍采用 AE2 原生的有符号 `long`，每种最多 **9,223,372,036,854,775,807**；超过时拒收该类型，迁移元件时若会超限则保留来源元件。类型计数使用 `int`，仍受 Java 容器和内存限制。

界面按 1024 进位，支持 `B / KiB / MiB / GiB / TiB / PiB / EiB / ZiB / YiB / RiB`。超过 RiB 命名范围时使用带指数的紧凑形式，例如 `1.3 ×10^33 RiB`，避免长数字挤出界面。

## 搬迁、备份与恢复

旧版库存文件与主机 NBT 会自动升级为新格式。升级前停服备份完整世界；升级后的外部库存不能交给旧版本读取。

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

Total and used bytes use BigInteger, removing the 8 EiB ceiling. Individual resource counts remain AE2-native signed longs, capped at 9,223,372,036,854,775,807 each; overflowing imports retain their source cells. Display units extend to ZiB/YiB/RiB, with compact scientific notation beyond the named range. Type counts use `int` and practical size depends on memory and Java collections.

Unfiltered output eventually reaches destination-accepted types despite other rejected types. Many types and backoff can delay discovery; use a precise whitelist for direct lookup. Old inventory files and controller NBT upgrade automatically; back up before upgrading and do not load upgraded inventory with an older mod.

Build the hollow 3×3×3 shell shown above: outward-facing controller at the center of any face, 23 native LuV machine casings, one ordinary GT input bus and one Storage Link Hatch or Export Bus, air in the center. The bus and interface may occupy any remaining shell cell, including top/bottom, edges and corners. Neither may be shared between vaults; conflicts prevent both structures from forming and recover after separation. Buses reserved by a registered native GT multiblock are rejected too. No cores. All three custom blocks use LuV assembler recipes (30,720 EU/t), with LuV hulls/circuits and components; there is no workbench fallback. Connect the interface to powered AE: one channel, 2 AE/t. ME virtual buses are excluded. Manual construction is required for this machine.

Forming replaces the entire structure appearance with a smooth blue/white/violet surface per face, using nine connected tiles. Controller and connector/port indicators stay visible; native blocks, bus tier, dye and saved texture are never rewritten. Existing vaults retain UUID/data but require replacing 23 legacy casings with LuV casings.

Insert supported item/fluid cells or AE2 1k–16384k components. Filled cells migrate their contents and upgrade cards first, then permanently contribute nominal bytes; housing is consumed. Cards become stored items. Cell partitions/filters are not copied into vault rules. Unsupported, creative, void and spatial sources remain intact. Right-click the controller for status and absorption controls. Each import batch forces a dimension save and may pause the server briefly.

Items and fluids share an exact budget: 8 items or 2048 mB per byte, no type overhead. There is no artificial type cap, but practical size depends on memory, disk and AE enumeration performance. Broken/unloaded structures or an inactive interface stop access until restored.

Normal harvesting retains the controller UUID; replacement reconnects the same inventory. Duplicate controllers cannot mount one UUID together. External files are `<world>/data/aestoragevault/<UUID>.snapshot` and `.wal`. Always back up and restore the complete stopped world including chunks and players. Missing/corrupt files lock rather than replace stock. Keep files/logs and restore a complete backup. Migration receipts prevent duplicate capacity credit; ordinary cross-container transfers are not a distributed transaction with all other mods. Partial rollback or abrupt power loss can still create inconsistencies.

[开发文档与存储格式 / Development and file format](../development/README.md)
