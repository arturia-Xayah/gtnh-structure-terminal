# ME 容量仓库 / ME Storage Vault

[中文首页](../../README.md) · [English README](../../README.en.md)

## 建造

在 NEI 搜索容量仓库主机、原生 LuV 机器外壳与访问接口，使用 NEI 查看配方。搭建 3×3×3 空心立方体：

- 主机在前面中央，朝外。
- 其余外壳位置放 23 个LuV 机器外壳、1 个标准 GT 输入总线、1 个 ME 访问接口。总线与接口可换位。
- 中央为空气，不放存储核心。

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

主机使用蓝白存储立方体面板，接口采用类似输入总线的凹入式插口、金属边框与触点，机身均为 LuV 外壳。成型后输入总线显示同一机身材质并保留输入、染色等标记；不改变总线实际等级或颜色，拆开结构后恢复其原生显示。

从 0.2.0 或独立版升级：主机 UUID、容量及外部库存不变，将 23 个旧式外壳替换为原生 LuV 机器外壳即可重新成型。旧式外壳保留注册以便已有世界加载，但不再是有效结构材料。

## 扩容与迁移

放入元件后，每秒自动检查并尝试吸收。支持 AE2 的 1k～16384k 存储组件，以及能通过 AE 接口读取容量和内容的物品/流体存储元件；AE2FC 的普通流体元件也可迁移。

非空元件先迁移库存和升级卡，之后永久消耗元件及外壳并增加标称字节容量。升级卡作为普通库存物品保留；元件分区和过滤配置不成为仓库过滤条件。创造、虚空、空间及未知通道元件不吸收，会留在总线中。

右键主机或已绑定的接口查看状态，可关闭自动吸收后集中放入元件，再开启扩容。每批新元件吸收时会同步保存维度并等待区块写盘，大存档可能出现一次停顿。

## 容量

| 内容 | 占用 |
|---|---:|
| 8 个物品 | 1 字节 |
| 2048 mB 流体 | 1 字节 |
| 新类型 | 无额外开销 |

物品与流体共用容量。使用精确分数计数，界面的已用字节向上取整。例如 1 字节容纳 1 个物品和 1792 mB 流体。没有人为类型限制，实际规模受服务器内存、磁盘和 AE 列表处理能力限制。

结构损坏、区块未加载或接口无频道/供电时暂停 AE 访问。修复后重新成型可恢复库存。

## 搬迁、备份与恢复

普通采掘主机得到的物品保留库存 UUID 和容量；重新放置并成型后访问同一库存。复制主机物品不会复制库存，同一 UUID 不允许两个主机同时挂载。创造模式无掉落拆除不提供主机物品，请保留备份。

实际文件：

```text
<世界目录>/data/aestoragevault/<UUID>.snapshot
<世界目录>/data/aestoragevault/<UUID>.wal
```

停服后整体备份世界，包括两个文件、区块与玩家数据。不能只备份主机 NBT，不能单独恢复旧库存文件。文件缺失、损坏或物品注册缺失时，仓库会锁定并报告，避免用空库存覆盖。遇到锁定时保留文件和日志，从完整备份恢复。

元件迁移回执用于防止恢复来源后再次加容量；普通 AE 搬运与其他容器并非跨 Mod 的统一原子事务，断电或部分回滚仍可能造成不一致。移除整合 Mod 后，机器与外部库存无法访问；恢复对应版本和完整世界后再使用。

## English

Build the hollow 3×3×3 shell shown above: front-center controller, 23 native LuV machine casings, one ordinary GT input bus and one ME access interface, air in the center. No cores. Both custom blocks use LuV assembler recipes (30,720 EU/t), with LuV hulls/circuits and components; there is no workbench fallback. Connect the interface to powered AE: one channel, 2 AE/t. ME virtual buses are excluded. Manual construction is required for this machine.

GTO-inspired blue/white overlays sit on the native LuV shell. A formed vault gives its input bus the same visual shell while retaining port/color indicators; the native bus tier, dye and saved texture are never rewritten. Existing vaults retain UUID/data but require replacing 23 legacy casings with LuV casings.

Insert supported item/fluid cells or AE2 1k–16384k components. Filled cells migrate their contents and upgrade cards first, then permanently contribute nominal bytes; housing is consumed. Cards become stored items. Cell partitions/filters are not copied into vault rules. Unsupported, creative, void and spatial sources remain intact. Right-click the controller for status and absorption controls. Each import batch forces a dimension save and may pause the server briefly.

Items and fluids share an exact budget: 8 items or 2048 mB per byte, no type overhead. There is no artificial type cap, but practical size depends on memory, disk and AE enumeration performance. Broken/unloaded structures or an inactive interface stop access until restored.

Normal harvesting retains the controller UUID; replacement reconnects the same inventory. Duplicate controllers cannot mount one UUID together. External files are `<world>/data/aestoragevault/<UUID>.snapshot` and `.wal`. Always back up and restore the complete stopped world including chunks and players. Missing/corrupt files lock rather than replace stock. Keep files/logs and restore a complete backup. Migration receipts prevent duplicate capacity credit; ordinary cross-container transfers are not a distributed transaction with all other mods. Partial rollback or abrupt power loss can still create inconsistencies.

[开发文档与存储格式 / Development and file format](../development/README.md)
