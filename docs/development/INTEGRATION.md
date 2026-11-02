# 整合与兼容迁移 / Integration and compatibility

## 名称与模块

产品显示名：**GTNH现代化升级**。英文文档名：**GTNH Modernization Upgrade**。发行文件前缀 `gtnh-modernization-upgrade`，本次版本 `0.3.0-alpha`，目标 GTNH `2.9.0`。

一个 JAR 包含四个 FML 注册模块。主模块显示统一产品名称，其他模块通过元数据 `parent=structureterminal` 归于主模块并沿用终端图标。保留模块 ID 是为了兼容已有世界、物品注册和 GUI 路由，并非要求玩家安装四个 Mod。

| 保留 ID | 内容 | 保留数据 |
|---|---|---|
| `structureterminal` | 结构终端、原料＋标记 | 终端物品、网络通道、`structureterminal.cfg` |
| `aeorderoptimizer` | ME 下单优化元件 | `core` 方块与 TileEntity、元件设置 |
| `plasmaforgepower` | 锻炉、普通和压缩聚变功率控制 | `plasmaforgepower:limits` 等现有 NBT、旧电压索引 |
| `aestoragevault` | 外部文件容量仓库 | `controller/access/casing`（保留），新增 `output`、TileEntity ID、UUID 与文件目录 |

所有模块共享 `dev.structureterminal.Tags.VERSION`。客户端和服务器使用同一整合版本。原独立版 JAR 必须移除，否则发生重复 Mod ID。主依赖包含 GT、AE2、StructureLib、ModularUI2 与 UniMixins；外部依赖不打包进 JAR。

## 图标

原图 `assets/structureterminal/textures/items/structure_terminal.png` 原样保留。各模块 `mcmod.info` 的 `logoFile` 都使用该资源。双语 README 也使用原蓝白终端图，不生成替代图标。

## 补丁与生命周期

终端原有 ASM 提示隔离核心插件保持不变。AE、功率控制及仓库外观使用独立 LateMixinLoader 和 JSON 配置，同时在一个 JAR 中发现。客户端 AE 标记补丁只在客户端加入；后台测试不能代替它的 OpenGL / 交互验证。

仓库仍使用原来的 Mod 实例、GUI handler、服务器 tick / 世界保存回调以及停服关闭流程。物品/方块均在原命名空间注册。功率预算算法不变。仓库 0.3.0 使用 BigInteger 容量与单类型库存数量，写入 AVS6，接受此前本地开发版 AVS5；不读取 0.2.x 的 AVS2/AVS3/AVS4 文件。旧版仓库需先导出库存，再升级、新建并重新投入容量来源。0.2.1 的外壳改为原生 LuV 机器外壳，配方使用 LuV 组装机；GT 输入/输出总线的统一外观由只读渲染补丁提供，不改写总线 NBT。

## 存档

仓库库存仍位于主世界保存目录 `data/aestoragevault/<UUID>.snapshot` 与 `.wal`。主机 NBT 保存 UUID、最大字节、账本序号、贡献统计和设置；实际库存仍在外部文件。不能通过更改产品名迁移或新建一套空文件。

功率设置仍放在原 GT 主机字段中，物质操纵者和数据宝珠的配置复制类型不变。输入颜色、原机成型、启动 EU、热切换和候选遍历规则不变。

此次验证同时加载所有模块，并重新执行各套服务器测试。库存格式升级后不能降级给旧版本读取；完整验证边界见 [VALIDATION.md](VALIDATION.md)。

## English

The product is renamed while four internal FML namespaces are retained for existing saves. All ship in one JAR with one shared version, the Structure Terminal icon and parent metadata. Remove former standalone JARs. Existing registration names, configuration paths, power NBT keys, voltage IDs, vault UUIDs and external-file locations remain unchanged. AE and power LateMixinLoaders coexist with the terminal core plugin. Dedicated-server checks exercise all modules together; client rendering remains a separate acceptance step.

## 0.2.3 存储端口

原 access 注册 ID 不变，增加 IPriorityHost 原生优先级界面与持久化；新增 output 方块和 TileOutput。输出不挂载私有库存，通过 AE 原生 poweredInsert 向目标网络批量注入。两种 ME 端口互斥；0.3.0 另允许一个普通 GT 物品输出总线退回空元件。当前容量及退回事务格式详见 [STORAGE_FORMAT.md](STORAGE_FORMAT.md)。细节见 [VAULT_IO.md](VAULT_IO.md)。

## 0.3.0 库存与退回

每种容量来源累计吸收数量与实际入账字节，原生详情页分页同步。可选物品输出总线由 NativeBusOwners 与仓库结构检查共同执行独占约束；退回通过 WAL 意图和原生总线 NBT 凭据分阶段交付，原位重启恢复。详情请求由独立限额队列切回服务端主线程，不在网络线程执行世界保存。

0.3.0 changes the inventory format: AVS6 accepts local-development AVS5, but does not migrate 0.2.x files. Export old stock before upgrading and create a new vault. The optional physical GT output bus is separate from the mutually exclusive ME ports and returns empty capacity sources through recoverable debit/delivery receipts.
