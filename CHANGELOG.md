# 更新日志 / Changelog

## 0.3.5-alpha — GTNH 2.9.0 · 2026-11-11

- 修复终端材料列表末页重复上一页条目；滚轮与方向键按固定分页定位，并校正通道中文名称。 / Fixed overlapping terminal material pages and aligned wheel/keyboard navigation; corrected Chinese channel labels.

- 高级终端新增四页原生通道设置，材料图标列表、通道名悬停提示、尺寸输入和辅助开关；修复附加通道在搭建计划中被清除，并接入原生投影错误标记。 / Four additional native-channel pages with item selectors, channel-name tooltips, dimensions and toggles; preserve subchannels in build plans and enable native hint-error highlighting.

- 聚合器物品图标与世界外观统一为白蓝紫材质；修正主机、ME 接口在渲染缓存中的成型外观。 / White/blue/violet vault item and formed surfaces, including controller and ME ports in cached chunk rendering.
- 高级终端增加本地深色模式，降低浅色亮度，修正材料图标照明；背包键（默认 E）关闭界面。 / Persistent dark mode, subdued light palette, corrected material-icon lighting and inventory-key close.
- 玻璃按功能性电压等级分组，同级变体使用二级菜单和按等级 Auto。 / Tiered glass groups with variant submenus and per-tier Auto.
- 取消保留仓室后按原版拆卸规则移除仓室，主机保留；不额外保存流体或打包库存，原生物品掉落走背包／AE 回收。 / Optional hatch removal follows native drops, retaining the controller without extra inventory/fluid packaging.
- AE 网络工具同时统计聚合器的物品与流体存储、已用类型及共享空闲容量。 / Native AE network-tool statistics include both vault storage channels.
- 优化器多配方报告简化为主产物与配方数；循环报告显示主产物链，彩色下划线链接定位样板方块并短暂高亮，跨维度显示名称和坐标。 / Compact clickable recipe/cycle reports with provider coordinates and temporary same-dimension highlights.

## 0.3.4-alpha — GTNH 2.9.0 · 2026-11-09

- 拆除新增“保留仓室”选项；关闭后回收空的基础 GT 仓室。AE 模式拆除只回收到网络，满仓／无连接时保留方块，实际存入被拒绝的余料保存在终端中供重试。 / Optional hatch retention and AE-only demolition recovery, including capacity checks and a persistent pending-return journal.
- 终端物品图标和完整提示改用 NEI 渲染与提示接口；新增建造／替换／拆除模式单选，支持仅替换线圈、材料返还、背包容量和拆除权限检查。 / Native NEI item rendering and full tooltips; exclusive Build/Swap/Remove modes with coil-only replacement, material recovery, inventory capacity and protection checks.
- 终端设置改为蓝白淡紫圆角界面：布尔勾选框、可展开的线圈／玻璃图标单选列表、悬停名称与翻页；玻璃选择接入 GTNH 原生通道。 / Rounded terminal UI with checkboxes, expandable coil/glass item selectors, hover names and paging; glass selections use native GTNH channels.
- 高级终端按 GTMThings-gto `1.5.4-GTO-Port` 公开实现重写：原版高级终端贴图、基础设置、右键投影与 Shift 右键同 tick 搭建；缺料位置继续跳过，支持无仓室模式与仅线圈替换。
- Reworked the advanced terminal against the public GTMThings-gto `1.5.4-GTO-Port` implementation: original advanced sprite, basic settings, right-click projection and same-tick sneak building; skip unavailable positions, suppress optional hatches and replace heating coils only.

- 修复集成样板右键后立即关闭：等待原版处理手持物品副本后再打开 4×9 容器，保留手持槽保护与即时保存。
- Fixed Integrated Pattern closing immediately after right-click: open after vanilla replaces the held stack, retaining held-slot protection and immediate saving.
- 聚合器原生 LuV 外壳改用 GT 的表面覆盖接口，与主机、仓室共用整面连续底纹；同步成型、拆除和区块卸载状态，保留功能图案。
- Route formed LuV casings through GT’s native surface overlay API with the same continuous skin as the controller and ports; refresh on formation, dismantling and chunk unload while retaining functional markings.

## 0.3.3-alpha — GTNH 2.9.0 · 2026-11-04

包含此前未独立发布的 0.3.1／0.3.2 开发改动。Includes the previously unpublished 0.3.1/0.3.2 development changes.

- 新增 4×9 集成样板：真实保存 36 张已编码样板，普通／二合一接口（方块与薄片）按原生配方展开；接口终端整包存取及内部输入／输出搜索，记忆卡按空容器和空白样板计费。合成为 4 空白样板、4 二合一接口、1 任意 IV 电路；普通样板风格图标增加集成标识。
- Added a 4×9 Integrated Pattern holding 36 real encoded patterns. Native recipe expansion for normal/dual interface blocks and parts, whole-holder terminal operations and child input/output search, and cost-aware Memory Card copying. Recipe: four blanks, four Dual Interfaces and one IV circuit.

- 新增 ME 打包胶带：沿用 GTO 使用的 ExtendedAE 原版胶带／包装物贴图、64 次耐久和 GTO 配方，支持单独打包薄片接口及完整接口、二合一接口、ME 驱动器；保留原生库存与配置，检查 AE／领地权限，失败不消费包装物。
- Added ExtendedAE-style ME Packing Tape with unchanged upstream assets, 64 uses and the GTO recipe. Supports individual interface parts, normal/dual interface blocks and ME drives, retaining native contents/settings and honoring placement/security checks.

- 装配线导入新增可选的改名样板自动生成：立即生成 1→1 加工样板放入背包，从终端、背包或 AE 网络消耗空白样板；检查当前网络和背包中的精确重复样板，缺料或空间不足时保留原配方，部分网络提取退回终端槽。
- Added opt-in generation of 1:1 renaming patterns on assembly-line import, with blank-pattern costs, exact deduplication, inventory-space checks and lossless handling of partial ME extraction.

- ME 下单优化元件新增默认关闭的“装配线自动重命名”：NEI 导入时保留首份原料，重复输入依次使用“重命名1、2…”；与成型机名称模具产物一致，保留数量和原有 NBT，并按终端所在网络启用。
- Added opt-in assembly-line input renaming on NEI import, controlled by the terminal's own optimizer grid. Duplicate items receive fixed Forming Press-compatible names while preserving counts and original NBT.

- 终端物品直接使用 GTO 原版贴图，保留两帧、每帧 6 tick 的动画；Mod 图标与 README 使用原图第一帧。
- 移除废弃 `aestoragevault:casing` 的方块和物品注册、名称及创建分支；不再出现在创造物品栏与 NEI。旧实例不再保留，原生 LuV 外壳、主机和仓库库存不受影响。
- Restored the original GTO terminal sprite with its two-frame, six-tick-per-frame animation. Mod logos and README use the unchanged first frame.
- Removed the retired `aestoragevault:casing` block/item registration and names. Old instances are not retained; native LuV casings, controllers and vault inventory are unaffected.

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

### 同时发布的结构与存储改进 / Structure and storage improvements

- 终端快捷操作改为：右键主机投影、Shift＋右键主机立即搭建、Shift＋右键空气打开配置。正常搭建取消每 6 tick／25 方块的等待，在一个服务端 tick 内完成原生搭建轮次；缺料、障碍和安全上限仍会停止。
- 对照 GTO 界面重排配置卡，采用原版 Ore UI 与工具栏图标，加入开关控件、缩略图和工具提示，保留部件与位置选择。
- 聚合器注册 StructureLib 结构，接入原生 NEI 多方块预览及结构终端搭建，提供三种布局示例；保留已安装组件与主机方向。
- 聚合器主机与 ME 端口改用 GT 原生像素材质，成型后采用上文统一平滑外观；修复客户端成型外观依赖服务端仓室数据的问题，成型状态变化立即同步。
- 沿用 0.3.0 的 AVS6 库存格式、注册 ID 和容量记录，无需重新建立 0.3.0 聚合器。

- Right-click controller to project; sneak-right-click controller for immediate construction; sneak-right-click air for settings. Native build rounds drain within one server tick instead of waiting six ticks per 25-block batch, stopping on shortages, obstructions or safety limits.
- Reworked GTO-style configuration cards with original Ore UI/tool icons, switches, minimap and tooltips, retaining part/position choices.
- Registered the vault with StructureLib for native NEI multiblock previews and terminal construction, with three example layouts and preserved existing components/controller orientation.
- Reused native GT pixel-art controller/port icons, with the unified smooth formed skin described above. Formed rendering now uses synchronized client state instead of server-only hatch inspection; state transitions sync immediately.
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
