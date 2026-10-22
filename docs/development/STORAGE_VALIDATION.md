# 验证记录 · Validation

2026-10-22，目标 GTNH 2.9.0-RC-2，GT5 5.09.54.205，AE2 rv3-beta-1080-GTNH，AE2FC 1.5.113-gtnh。**21 项核心测试 + 26 项后台整包测试全部通过**。两个独立服务器进程正常退出。全程没有启动 Minecraft 客户端。

## 核心测试：21 项

直接读写真实临时文件，覆盖：

- 10,000 个不同资源键，没有 63 类型限制或类型字节税。
- 精确物品/流体共用容量，部分插入、满仓拒收、提取归零。
- 模拟存取不修改库存、不追加日志。
- 非空元件容量与内容同笔导入，回执跨关闭/重开保持幂等。
- 导入总容量不足、单资源数量溢出、总容量溢出均不产生部分加账。
- `Long.MAX_VALUE` 级容量/计数及大整数使用量。
- 资源键不可变，返回的库存副本不能修改账本。
- 丢失快照/日志时拒绝创建空替代；损坏快照、损坏完整日志、截断快照均拒绝加载。
- 截断未完成的日志尾记录；快照已替换而旧日志尚未截断时不重复应用。
- 文件关闭后继续写入被拒绝；同文件重复挂载的文件锁保护。
- 8 个并发工作线程不能超额插入。
- 5,000 次随机存取与独立参考账本比对，穿插快照、关闭和重开。

原始报告：[core-tests.xml](../../verification/storage-baseline/core-tests.xml)。

## GTNH 后台首次运行：22 项

在原整包之外建立全新平坦测试世界，加载真实 GT、AE2 和 AE2FC：

- 真实 3×3×3 结构、原生 EV 输入总线、原生 AE 控制器及创造能源建立的供电网络。
- 自动吸收 2 个 1k 组件、1 个非空 4k 物品元件及 1 个非空 1k 流体元件，容量合计 7168 字节。
- 迁移并核对 **12,345 铁锭、900,000 mB 水、1 张模糊卡**。
- 无关物品和创造元件保留，不被吞掉。
- 通过真实 AE 网络查询迁入物品，存取物品与流体。
- 真实 AE 网络插入额外 **1000 种不同 NBT 的纸**，共至少 1003 种资源。
- 填满共享空间后不吞溢出物品，`canAccept` 正确返回 false。
- 人为恢复已入账的元件，自动清理来源，容量不重复增加；最终容量 8192 字节。
- 破坏外壳立即阻止 AE 提取；恢复结构可再次访问。
- 填满中心、加入第二访问接口被拒绝成型。
- NBT 复合标签顺序不影响键、列表顺序影响键，编码可往返。
- 主机序列化小于 512 字节，不含实际库存。
- 掉落物保留 UUID/容量，采掘流程保留 TileEntity 供生成掉落。
- 同 UUID 第二主机拒绝挂载；外部数据缺失和非法 UUID 均锁定。
- 服务端 GUI 暂停控制有效，超距离操作被拒绝。
- 卸载控制器关闭并释放库存，再放置主机从外部文件恢复全部条目。

原始报告：[vault-tests.json](../../verification/storage-baseline/vault-tests.json)。模拟恢复来源用于验证迁移回执，不等于物理断电故障注入。

## 停服并重新启动：4 项

同一测试世界启动第二个全新 Java 进程，未重新建造机器，验证：

1. UUID 不变。
2. 容量和超过 1000 种资源全部恢复。
3. 物品/流体数量不变。
4. 真实 AE 网络重新连接并能查询库存。

原始报告：[vault-tests-restart.json](../../verification/storage-baseline/vault-tests-restart.json)。

## 发行文件检查

- 正式构建通过代码格式、Checkstyle、单元测试及发行 JAR 检查。
- 16 个生产 class，全部 Java 8 字节码（major 52）。
- 无 `packtest` 测试类。
- 生产 class 的字节与上述后台测试 JAR 中对应 class **全部一致**。
- 后台 Java 版本、JAR SHA-256 和 class 哈希见 [distribution.json](../../verification/storage-baseline/distribution.json)。

## 验证边界

完成服务端 GUI 权限/按钮交互验证；未启动客户端检查贴图和 GUI 显示。未验证其他 GTNH 版本、全部第三方特殊元件、真实断电或任意部分存档回滚。普通跨容器搬运不具备与其他 Mod/原版存档共同提交的原子性。数据格式、保存时机及恢复要求见 [FORMAT.md](STORAGE_FORMAT.md)。

## English

All **21 core tests and 26 full-pack dedicated-server checks passed** on GTNH 2.9.0-RC-2, including a second JVM process restoring the same world. Real AE2 item cells, AE2FC fluid cells, native GT input bus, channel/power connection and AE network inventory operations were exercised. The fixture migrated 12,345 iron ingots, 900,000 mB water and an upgrade card; stored 1000 additional NBT-distinct item types; tested full-capacity rejection, invalid structures, duplicate UUID protection, missing-file locks and controller unloading/replacement.

The production JAR contains 16 Java-8-compatible classes, no test fixture, and identical production bytecode to the server-tested artifact. No client was launched. Client visuals, arbitrary third-party cells, actual power-cut scenarios and other pack versions remain unverified. Raw reports and artifact hashes are linked above.
