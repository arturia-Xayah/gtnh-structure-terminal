> 历史开发构建记录，保留当时的规则、哈希与验证范围；当前发行版见 [最新验证](VALIDATION.md)。

# 当前开发版验证 / Current development validation

当前本地 0.3.0-alpha、GTNH 2.9.0 构建已完成原生元件贡献详情页，并将人造宇宙元件改为贡献全部原始容量。尚未推送或发布到 GitHub。

## 最终规则

| 来源 | 单件贡献字节 | 条件 |
|---|---:|---|
| 数字奇点物品元件 | 17,592,186,044,416 | 仅空库存 |
| 数字奇点流体元件 | 68,719,476,736 | 仅空库存 |
| 人造宇宙物品元件 | 576,460,752,303,423,487 | 仅空库存，完整原始容量 |
| 人造宇宙流体元件 | 4,503,599,627,370,495 | 仅空库存，完整原始容量 |
| Avaritiaddons 无尽箱子 | 2,147,483,646 | 不带库存/方块 NBT 的空箱 |

数字奇点保留 `(原容量 + 1) / 32768`，不使用此前讨论的按类型数折算公式。普通元件仍迁移库存和升级卡；四种高阶元件的升级卡仍作为真实物品迁移。统计保存实际入账字节，不追溯重算旧记录。AVS5 文件格式保持不变。

## 游戏内界面

主界面标题栏的 **元件明细** 按钮打开原生只读容器界面。每页六行显示原生元件图标、名称、吸收数量、累计贡献字节、占总容量比例和比例条；悬停显示完整名称、注册型号及数值。返回/上一页/下一页使用服务器校验的原生按钮操作，不接受远距离请求。

较小 GUI 使用 304×224 布局，普通布局为 304×266，六行图标、文字和底部操作区均在边界内。数字格式、占比和图标仅在页面数据变化时重建，避免每帧做 BigInteger 格式化和注册查询。页面仍按最多三行分包，只有查看者收到详情；主机及正常掉落物 NBT 镜像统计，外部凭据事务负责实际计数。

## 验证与安装包

历史本地构建 `universe-full-capacity`（未作为发行附件上传）：461,011 字节，165 个 Java 8 字节码类。

SHA-256：`d6251a5e01f2311c8f6b02250a7fdfcfdf80d4d443c8563499e87e638185fb7a`

- **117 项单元测试通过**：最终容量规则、空库存判定范围、Long.MAX_VALUE 边界、原子计数/凭据去重、WAL/检查点恢复、失败导入不改统计、BigInteger 数量/占比，以及小窗口/普通窗口的六行布局、图标/文字/按钮边界和悬停间隔。
- **68 项真实 GTNH 后台检查通过**：仓库 47 + 5、原有 AE 存取/导出 14 + 2，四个进程、两次全新进程重启。
- 四种高阶元件均验证原始/折算容量与非空拒收，拒收不修改来源。通过真实输入总线吸收数字奇点、两种人造宇宙和两个无尽箱子，检查实际七种容量来源的计数、字节、第二页及主机 NBT，并在重启后核对两种宇宙的完整容量贡献。
- 满仓检查使用多次 long 请求填入超过 Long.MAX_VALUE 的流体数量，验证精确预算满额、canAccept=false、拒收不吞物及全部取回恢复。
- 页面检查包括只读/距离校验、六行分包与末页裁剪、30,000 位整数的原版数据包往返、普通广播不含整张统计表。
- 正常安装包不含测试入口；全部 165 个生产类与执行的冻结测试包逐字节相同。与前一贡献版相比，生产类变化仅涉及聚合器。

未启动游戏客户端；GUI 字体、物品渲染与 OpenGL 的实际画面未验收。未重跑性能矩阵，不将旧吞吐或查询数据当作本构建实测结果。此前网页记录仍是旧规则下的示例设计，不作为此构建的实际运行画面。

[清单与类哈希](../../verification/vault-universe-full-capacity/manifest.json) · [后台汇总](../../verification/vault-universe-full-capacity/summary.json) · [玩家指南](../features/STORAGE.md) · [存储格式](STORAGE_FORMAT.md)

## Historical validation

[首次贡献界面版](VALIDATION_0.3.0_CONTRIBUTIONS.md) · [缓存配置版](VALIDATION_0.3.0_CACHE.md) · [读取优化版](VALIDATION_0.3.0_READ.md) · [全精度数量版](VALIDATION_0.3.0_QUANTITY.md) · [已发布 0.2.6](VALIDATION_0.2.6.md)

The local 0.3.0 build completes the native contribution GUI and restores full nominal bytes for item/fluid Universe cells while keeping both empty-only. Singularity cells retain `(nominal bytes + 1) / 32768`. It passes 117 unit tests and 68 dedicated-server checks across four processes including two restarts. Real bus imports and seven-source second-page history are checked, as is filling the enlarged budget with more than Long.MAX_VALUE fluid units, rejecting overflow and restoring all test stock. The six-row GUI uses responsive shared geometry, cached presentation strings/icons, native item rendering, share bars and bounded viewer-only packets. All 165 production classes match the executed fixture and target Java 8. No client visual acceptance, new performance benchmark or GitHub publication is claimed.
