# 0.2.5 原生主机动画与整合验证 / Native dashboard validation

日期：2026-10-23，目标 GTNH 2.9.0-RC-2，Java 25.0.4.1。全部运行验证使用 nogui / java.awt.headless=true，未启动 Minecraft 客户端。

| 范围 | 结果 |
|---|---|
| 单元测试 | **94 项通过** |
| 优先级、Reshuffle 与输出 | **14 项通过** |
| 输出端口独立进程重启 | **2 项通过** |
| 仓库首次进程 / 重启 | **38 / 4 项通过** |
| 锻炉 | **26 项通过** |
| 普通与压缩聚变 | **126 项通过** |
| AE 下单、发配与原料标记 | **51 项通过** |
| 结构终端 | **3 台主机、10 个操作阶段通过** |
| 正式包 | 145 个 Java 8 生产类，与本轮运行夹具逐字节一致 |

本轮共 261 项后台断言及 10 个结构操作阶段。没有把旧版额外世界升级的 8 项检查重复计入；历史结果见 [0.2.4](VALIDATION_0.2.4.md)。

## 新界面的检查

9 项新增单元测试验证首次快照直接显示当前值、中途更新连续衔接、到达精确终值、int 类型计数边界、2^2000 级容量的有限比例、空/不一致快照的显示钳制、关闭动画/结构失效立即停动，以及普通/紧凑布局下的槽位隔离、几何边界与顶点预算。

原有真实容器操作检查增加：36 个玩家槽位身份不变，本地动画用的 action 2 在服务端被拒绝且不修改吸收设置。构建和后台模块加载也覆盖新的 CommonProxy 配置入口，服务端无需加载客户端绘制类。

发行器逐字节比较 0.2.4 的 TileController、TileOutput、TileAccess、VaultStore、CapacityValue、VaultInventory、Repositories；它们没有变化，库存算法与同步频率保持不变。动画没有世界 tick 监听器或动画专用网络包。

## 共享几何预览

生产 VaultMesh 的背景、环形容量、网格、扫描线、状态光与库存槽背景进入可复用顶点缓冲，再由游戏单批绘制。普通与紧凑布局、中英文、极大容量、未成型和开关动画状态均用同一生产几何离线绘制并检查。文本与按钮的 Java2D 替代绘制用于布局核对，不是 Minecraft 字体/客户端截图。

[动画](../assets/vault/dashboard/animation.gif) · [英文动画](../assets/vault/dashboard/animation-en.gif) · [普通](../assets/vault/dashboard/full.png) · [紧凑](../assets/vault/dashboard/compact.png) · [极大容量](../assets/vault/dashboard/huge-en.png) · [未成型](../assets/vault/dashboard/invalid.png)

关闭或失效状态停止循环效果并显示当前实际值；BigInteger 比例和数值只在数据/过渡步骤改变时重算，容量文本单独缓存。背景绘制具有 8192 顶点上限。字形、按钮、原生物品渲染不属于这一批次。此处没有把离线几何检查当作游戏 GPU 帧率基准。

## 边界

未启动客户端，未实际验收 OpenGL 驱动、Minecraft 字体与资源包、NEI 覆盖层、鼠标物品交互及实际 GUI 缩放。服务端槽身份检查不等于客户端点击验收。绘制代码局部保存/恢复 GL 状态，并显式关闭面剔除，但实际 GPU 路径仍需客户端验收。

动画配置仅保存到客户端 config/gtnh-modernization-client.cfg，客户端不把此设置写入主机 NBT 或其他玩家的设置。数据仍来自原生仓库摘要。长实际值的悬停提示采用科学计数摘要；它不经过动画插值，但不保证显示所有十进制位。

## 证据

- [正式包与检查清单](../../verification/modernization-0.2.5/manifest.json)
- [UI 预览文件哈希](../../verification/modernization-0.2.5/dashboard-preview.json) / [实现与重现方法](VAULT_DASHBOARD.md)
- [仓库和容器](../../verification/modernization-0.2.5/vault/vault-tests.json) / [仓库重启](../../verification/modernization-0.2.5/vault/vault-tests-restart.json)
- [输出](../../verification/modernization-0.2.5/vault_io/vault-io-tests.json) / [端口重启](../../verification/modernization-0.2.5/vault_io/vault-io-tests-restart.json)
- [锻炉](../../verification/modernization-0.2.5/forge/forge-power-tests.json) / [聚变](../../verification/modernization-0.2.5/fusion/fusion-power-tests.json)
- [AE](../../verification/modernization-0.2.5/ae/ae-order-report.json) / [结构操作](../../verification/modernization-0.2.5/structures/machine-validation/report.json)

## English

The 0.2.5 artifact passed 94 unit tests, 261 dedicated-server checks and ten structure-operation phases. New tests cover transition continuity, exact completion, extreme BigInteger ratios, reduced motion, invalid states, responsive slot geometry and mesh bounds. The real container preserves all 36 player-slot identities and rejects animation action 2 on the server. Storage/sync classes match 0.2.4 byte for byte. Headless illustrations share production geometry but use substitute fonts; no game client, GPU, resource-pack or mouse acceptance was performed.
