# 更新日志 / Changelog

## 0.3.0-alpha · GTNH 2.9.0

### 中文

- **元件退回**：聚合器可额外安装一个普通 GT 物品输出总线。在元件明细中点击类型，输入数量或点击“全部”，将容量来源退回为空元件。空闲容量或总线空间不足时部分退回，保留仓库库存；更新数量、容量及占比。加入持久化退回凭据与重启恢复。
- **元件贡献明细**：新增原生分页界面，显示元件图标、剩余吸收数量、累计贡献字节和占总容量比例，支持小窗口布局。
- **容量来源规则**：数字奇点物品／流体元件仅接收空库存，贡献 `(原容量 + 1) / 32768`；人造宇宙物品／流体元件仅接收空库存，贡献完整原容量。支持空的 Avaritia Addons 无尽箱子，单个贡献 2,147,483,646 字节。普通非空元件仍迁移物品、流体及升级卡。
- **超 long 库存**：实际物品／流体数量、容量和占用采用 BigInteger；AE 可见数量饱和到 long 上限，超出部分继续保留。手持物品潜行右键主机，可查询精确库存；流体容器同时查询内部流体。
- **读取优化与缓存配置**：复用类型索引、原生库存视图与解码原型，减少重复遍历、解码和临时复制。新增 `config/gtnh-modernization.cfg`，可调整 LRU 条数、局部／全局缓存权重预算与单条准入上限。
- 更新中英使用说明及开发验证文档。结构终端、AE 下单优化、原料“＋”标记及锻炉／聚变功率控制继续包含在同一安装包中。

**升级说明**：客户端与服务器安装相同版本，移除旧整合包及重复的独立模块 JAR。聚合器使用新的 AVS6 外部库存格式，可读取此前本地开发版 AVS5，**不能直接读取 0.2.x 的 AVS2／AVS3／AVS4**。已有旧版聚合器需先在旧版导出库存，再升级、新建仓库并重新投入容量来源。请停服备份完整世界；不要单独回滚外部库存或主机 NBT。

**使用退回功能**：在 3×3×3 结构中用一个普通 GT 物品输出总线替换外壳，保留原输入总线及一个 ME 链接仓／ME 主动输出总线；此时使用 22 个 LuV 外壳。普通输出总线也必须独占。退回空元件，不恢复原库存、升级卡或过滤配置；相关物品仍在仓库中。

[使用指南](docs/features/STORAGE.md) · [缓存配置](docs/features/CACHE_CONFIGURATION.md) · [测试与开发记录](docs/development/VALIDATION.md)

### English

- **Return capacity sources** through one optional ordinary GT item output bus. Click a row in Cell details, enter a quantity or use All. Returns are limited by free capacity and physical output room, report partial deliveries, preserve stored stock and update contribution totals. Durable debit/delivery receipts support restart recovery.
- **Paged contribution UI** with native item icons, remaining absorbed counts, credited bytes and capacity shares, including compact layout support.
- **Capacity rules**: empty-only item/fluid Singularity cells credit `(nominal bytes + 1) / 32768`; empty-only Universe cells credit full nominal bytes. Empty Avaritia Addons Infinity Chests contribute 2,147,483,646 bytes each. Ordinary filled cells still migrate stock and upgrade cards.
- **BigInteger stock** for exact item/fluid quantities, capacity and usage. Native AE quantities saturate at signed-long maximum without discarding the reserve. Sneak-right-click while holding an item/container to query exact item/fluid stock.
- **Read-path optimization and configurable caches**: reused type indexes, native inventory views and decoded prototypes reduce repeated enumeration, decoding and copying. Configure LRU entries, local/global cache weights and per-entry admission in `config/gtnh-modernization.cfg`.
- Updated bilingual player guides and developer evidence. The Structure Terminal, AE planner/dispatch changes, craftable-input badges and forge/fusion controls remain bundled.

**Upgrade**: install the same JAR on clients and server; remove previous combined/duplicate standalone modules. AVS6 reads local-development AVS5 files but **does not migrate AVS2/AVS3/AVS4 from 0.2.x**. Export old stock before upgrading, then create a new vault and supply capacity sources. Back up the complete stopped world; never restore external inventory files or controller NBT independently.

**Returning cells**: replace one casing with one ordinary GT item output bus, leaving 22 LuV casings. Keep the input bus and exactly one Storage Link Hatch / ME Export Bus. The physical output bus must be exclusive. Returned sources are empty; original stock and upgrade cards stay in the vault, and source filter/configuration NBT is not reconstructed.

[Player guide](docs/features/STORAGE.md#english) · [Cache configuration](docs/features/CACHE_CONFIGURATION.md#english) · [Validation](docs/development/VALIDATION.md)

## 0.2.6-alpha · GTNH 2.9.0

适配 GTNH 2.9.0 正式版，更新依赖并验证后台存档升级。 / Updated compatibility and dependencies for GTNH 2.9.0 stable, with dedicated-server saved-world upgrade checks.

[历史发布 / Previous release](https://github.com/arturia-Xayah/gtnh-structure-terminal/releases/tag/v0.2.6-alpha-GTNH-2.9.0)
