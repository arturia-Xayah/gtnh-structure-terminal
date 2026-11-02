# 0.3.0-alpha · GTNH 2.9.0 验证 / Release validation

本次发行整合了 BigInteger 库存、读取优化与缓存配置、容量来源规则、原生元件明细，以及可选普通 GT 输出总线的空元件退回功能。

## 最终安装包

[下载 JAR](https://github.com/arturia-Xayah/gtnh-structure-terminal/releases/download/v0.3.0-alpha-GTNH-2.9.0/gtnh-modernization-upgrade-0.3.0-alpha-GTNH-2.9.0.jar)

- 文件：`gtnh-modernization-upgrade-0.3.0-alpha-GTNH-2.9.0.jar`
- 体积：483,991 字节；174 个生产类，全部为 Java 8 字节码，不含后台测试入口。
- SHA-256：`b8065c97af913acaa2b47088a96e7733977ce633a6d344f4d9179199387d9462`
- GTNH 2.9.0 正式版完整后台服务器，Java 25.0.4.1；四个内部模块同时加载并核对官方依赖。

## 自动验证

**123 项单元测试、302 项后台检查，以及 3 台机器的 10 个结构操作阶段全部通过。** 后台共 12 个进程，包含仓库、ME 输出、缓存及超 long 数量的四次独立进程重启。

| 范围 | 检查数 |
|---|---:|
| 聚合器结构、库存、容量来源、明细及退回 | 55 + 7（重启） |
| 原生 AE 优先级、Reshuffle 与主动输出 | 14 + 2（重启） |
| 缓存预算、关闭与重新加载 | 7 + 6（重启） |
| 超 long 单类型库存及 AE 可见窗口 | 6 + 2（重启） |
| 锻炉功率控制 | 26 |
| 普通与压缩聚变功率控制 | 126 |
| AE 下单、路径检查与批量发配 | 51 |
| 结构终端 | 3 台机器，10 个操作阶段 |

单元测试覆盖精确容量、分数用量、BigInteger 数量、来源计数与去重、WAL/检查点恢复、缓存预算、分页布局和数量输入。新增退回测试包含空闲容量不足、历史平均贡献向上取整、超过 long 的吸收数量、待交付时禁止 IO/吸收，以及 AVS5 读取并检查点升级为 AVS6。

真实整包检查通过普通 GT 输入总线吸收七种来源，核对高阶元件空库存限制、完整人造宇宙容量、无尽箱子和分页明细。退回检查包括：

- 未安装普通输出总线不扣容量；ME 输出端口不能代替物理出口。
- 合并已有堆叠、仅一个空位、满总线及总线锁定为其他物品。
- 库存占用限制下的部分退回；保留物品/流体与不足一字节的用量。
- 请求超过来源数量自动限额；退完后删除来源记录，空元件可重新吸收。
- 第二个普通输出总线拒绝成型，原生 GT 多方块占用时不能共用。
- 两个重启窗口：已扣容量尚未交付、已保存元件及总线凭据尚未确认。恢复后均只交付一次，重复恢复不再扣容量或插入物品。
- 待交付时原生总线读槽/拉取受控，但保存仍包含真实物品；恢复后释放保护并重新绑定曾解绑的 AE 接口。

## 生产包与实测夹具的关系

[清单](../../verification/modernization-0.3.0-release/manifest.json) 包含每个生产类、测试夹具的哈希以及所有后台报告。**173/174 个生产类与执行夹具逐字节相同**，所有资源相同。唯一差异为仅客户端的 `ContributionScreen.class`：后台执行后将数量输入框文字从深色改为浅蓝白，以适配原生深色输入框；该显示改动另经正式构建、静态检查及完整单元测试，不称为后台执行过的 GUI 代码。差异双方哈希显式记录在 `fixture_differences`。

除版本常量在模块入口中内联外，结构终端、AE 下单及功率模块的原有生产类与 0.2.6 一致；本次仍重新跑过这些模块的后台回归。

## 存档与验证边界

AVS6 接受此前本地开发版 AVS5，在检查点时升级；不迁移 0.2.x 的 AVS2/AVS3/AVS4。旧版用户需先导出库存，升级后新建聚合器并重新投入容量来源。退回凭据只用于所述原生总线路径和重启恢复，不替代完整世界备份。大量区块同步存档的暂停时间未作为本轮性能指标测量。

未启动游戏客户端；原生 GUI 字体、物品渲染、输入交互及 OpenGL 实际画面未验收。本轮没有重跑读取性能矩阵，旧性能结果仅保留其历史范围，不能当作本发行包的新 MSPT 测量。

[后台汇总](../../verification/modernization-0.3.0-release/summary.json) · [更新日志](../../CHANGELOG.md) · [玩家指南](../features/STORAGE.md) · [存储格式](STORAGE_FORMAT.md)

## 历史记录

[人造宇宙完整容量](VALIDATION_0.3.0_UNIVERSE.md) · [首次贡献明细](VALIDATION_0.3.0_CONTRIBUTIONS.md) · [缓存配置](VALIDATION_0.3.0_CACHE.md) · [读取优化](VALIDATION_0.3.0_READ.md) · [全精度库存](VALIDATION_0.3.0_QUANTITY.md) · [0.2.6](VALIDATION_0.2.6.md)

## English

The release passes **123 unit tests, 302 dedicated-server checks and 10 structure-operation phases across 3 machines**. Twelve server processes include four fresh-process restarts. The suites cover real cell imports, final high-end capacity rules, paged history, partial returns constrained by exact free space and native bus room, locked/full buses, exclusive ownership, durable return recovery at two interruption windows, access-hatch rebinding, native AE operations, caches, quantities beyond long, forge/fusion controls, planning/dispatch and structure operations.

The installable JAR has 174 Java-8 production classes and no fixtures. All resources and 173 classes match the executed fixture. The only later change is the client-only contribution screen's input text color; its two hashes and compile/static-check scope are explicitly recorded. No game client or new performance matrix was run. AVS6 reads local-development AVS5 but does not migrate 0.2.x files. Player upgrade/backup requirements are documented in the storage guide.
