# 更新日志 / Changelog

## 0.3.2-alpha — GTNH 2.9.0 (Unreleased)

- 聚合器成型外壳改为整面连续的蓝白紫平滑材质；主机、ME 端口、原生输入／输出总线共用同一底纹与绘制方式，保留功能标识。相邻区域共享纹理坐标与 mipmap，四边使用一致底色。
- Added a continuous smooth blue/white/violet formed vault skin. Casings, controller, ME ports and native buses share the same surface renderer while retaining functional overlays; shared UV boundaries/mipmaps and matching edge colors prevent texture seams.

- 新增客户端异步更新器，兼容 GTNH Custom Main Menu；提供中英双语更新界面、说明滚动、下载进度、取消与跳过选项。
- 连接较新服务器失败后查询其 Forge 状态，按服务器的确切版本查找兼容发行包；可选择自动下载，保留严格版本校验。
- 新增 SHA-256、JAR 元数据和重复安装检查。独立助手等待游戏完全退出后备份旧包并原子替换；安装保持原文件名，原启动器负责重启。
- CI 生成带 GTNH 版本的安装包；包内更新描述与实际 Mod 版本一起生成、校验。

- Added asynchronous client updates integrated with GTNH Custom Main Menu, with bilingual notes, progress, cancellation and skip controls.
- Resolve the exact newer server release from public Forge status after connection rejection; optional automatic download preserves strict compatibility checks.
- Verify SHA-256, embedded compatibility metadata and duplicate installs. An independent helper waits for process exit, backs up and atomically replaces the installed JAR; restart through the original launcher.
- Generate version-bound updater metadata and correctly named installation assets in CI.

## 0.3.1-alpha · GTNH 2.9.0（待发布 / Unreleased）

### 中文

- 终端快捷操作改为：右键主机投影、Shift＋右键主机立即搭建、Shift＋右键空气打开配置。正常搭建取消每 6 tick／25 方块的等待，在一个服务端 tick 内完成原生搭建轮次；缺料、障碍和安全上限仍会停止。
- 对照 GTO 界面重排配置卡，采用原版 Ore UI 与工具栏图标，加入开关控件、缩略图和工具提示，保留部件与位置选择。
- 聚合器注册 StructureLib 结构，接入原生 NEI 多方块预览及结构终端搭建，提供三种布局示例；保留已安装组件与主机方向。
- 聚合器主机、ME 端口和成型外壳改用 GT 原生像素材质；修复客户端成型外观依赖服务端仓室数据的问题，成型状态变化立即同步。
- 沿用 0.3.0 的 AVS6 库存格式、注册 ID 和容量记录，无需重新建立 0.3.0 聚合器。

### English

- Right-click controller to project; sneak-right-click controller for immediate construction; sneak-right-click air for settings. Native build rounds drain within one server tick instead of waiting six ticks per 25-block batch, stopping on shortages, obstructions or safety limits.
- Reworked GTO-style configuration cards with original Ore UI/tool icons, switches, minimap and tooltips, retaining part/position choices.
- Registered the vault with StructureLib for native NEI multiblock previews and terminal construction, with three example layouts and preserved existing components/controller orientation.
- Reused native GT pixel-art controller/port/casing icons. Formed rendering now uses synchronized client state instead of server-only hatch inspection; state transitions sync immediately.
- Retains 0.3.0 AVS6 inventories, registration IDs and capacity ledgers.

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
