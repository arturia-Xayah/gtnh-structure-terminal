> 历史开发构建记录，保留当时的规则、哈希与验证范围；当前发行版见 [最新验证](VALIDATION.md)。

# 当前开发版验证 / Current development validation

当前本地 0.3.0-alpha、GTNH 2.9.0 构建增加容量来源规则与元件贡献统计。尚未推送或发布到 GitHub。

- 奇点/人造宇宙元件仅接受空库存，容量为 `(原容量 + 1) / 32768`。普通元件仍可迁移库存，升级卡仍作为真实物品迁移。
- Avaritiaddons `InfinityChest` 仅接受无库存/方块 NBT 的空箱，每件贡献 2,147,483,646 字节。Avaritia `Infinity_Chest` 是胸甲，不作为容量来源。
- 吸收数量、实际贡献字节与凭据/容量/迁移库存原子提交，主机和掉落物 NBT 保存统计镜像。六行详情页只发给查看者，拆成每包最多三行；普通状态广播不构建或发送整张统计表。
- 当前格式为 AVS5；按未部署开发版的要求不迁移 AVS2/AVS3/AVS4。

## 构建与验证

历史本地构建 `cell-contributions`（未作为发行附件上传），457,993 字节，163 个 Java 8 字节码类。

SHA-256：`44f9bc064772af60dae38121faac52dfd7d18ec8efe4843e04e9ee4b13357cc1`

- **115 项单元测试通过**：包括修正后的四种容量公式、Long.MAX_VALUE 加一边界、凭据去重、同型号累计与不同型号区分、WAL 重放和检查点、失败导入不修改统计、BigInteger 数量及极小占比。
- **68 项真实 GTNH 后台检查通过**，四个进程、两次全新进程重启：仓库 47 + 5、原有 AE 存取/导出 14 + 2。
- 新增后台覆盖四种高阶元件空/非空处理及拒收不修改源、无尽箱子空/带 NBT 处理、奇点和两个箱子的真实输入总线吸收、主机 NBT 与普通广播隔离、只读分页及距离校验、六行分包/末页裁剪及 30,000 位随机整数的原版数据包往返、重启保留统计。
- 正常 JAR 的全部 163 个生产类与实际执行的冻结测试 JAR 逐字节相同；正常 JAR 不含测试入口。最终生产包另外更新了中英文使用提示。
- 网页交互通过静态 DOM 检查：精确容量、分页边界、行选择、总览切换与状态保存。使用示例记录；贴图取自正式包本地模组资源。未做浏览器截图验收，未启动游戏客户端、GUI、字体或 OpenGL 验收。
- 本轮未重跑性能矩阵；既有查询/遍历性能数据不能作为该构建重新实测的结果。

[机器可读清单与类哈希](../../verification/vault-cell-contributions/manifest.json) · [后台汇总](../../verification/vault-cell-contributions/summary.json) · [玩家指南](../features/STORAGE.md) · [数据格式](STORAGE_FORMAT.md)

## Historical validation

[缓存配置版](VALIDATION_0.3.0_CACHE.md) · [读取优化版](VALIDATION_0.3.0_READ.md) · [全精度数量版](VALIDATION_0.3.0_QUANTITY.md) · [已发布 0.2.6](VALIDATION_0.2.6.md)

The local 0.3.0 contribution build passes 115 unit tests and 68 real-pack dedicated-server checks, including two fresh-process restarts. All 163 production classes match the executed fixture byte for byte and target Java 8. Capacity, count and inventory attribution share durable import transactions. High-end cells use `(nominal bytes + 1) / 32768` and reject nonempty stock. The controller mirrors per-item/metadata BigInteger counts and credited bytes, while six-row detail pages use bounded three-row packets. AVS5 intentionally rejects earlier development formats. The web preview uses sample records and has static DOM interaction checks only; no client or visual acceptance or new performance benchmark is claimed. This build has not been published to GitHub.
