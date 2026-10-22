# 0.2.2 连续材质与独占部件验证 / Connected surface and exclusive components

日期：2026-10-22，目标 GTNH 2.9.0-RC-2，Java 25.0.4.1。所有服务器使用 `nogui` 和 `java.awt.headless=true`，未启动游戏客户端。

| 范围 | 结果 |
|---|---|
| 单元测试 | **76 项通过** |
| 仓库首次进程 | **36 项通过** |
| 仓库独立进程重启 | **4 项通过** |
| 锻炉 | **26 项通过** |
| 普通与压缩聚变 | **126 项通过** |
| AE 下单、发配和原料标记 | **51 项通过** |
| 结构终端 | **3 台主机、10 个操作阶段通过** |
| 发行文件 | 130 个 Java 8 生产类，与实际运行夹具逐字节一致 |
| 连续表面 | 9 张运行时 64×64 切片；原生外壳客户端渲染入口签名通过静态检查 |

## 新增结构检查

六个朝向分别将输入总线和 ME 接口放遍主机之外的 25 个外壳位置，另一功能部件放在相邻的不同位置，共 **300 种真实布局**。这些布局包含顶面、底面、棱边与角落。不是全部 3600 种位置配对的穷举。

共用总线和共用接口分别构造两座完整候选仓库；两个主机都拒绝成型，反转检查顺序仍拒绝。移除另一主机后，保留的仓库恢复成型。还检查非面中央主机无效，以及原生 GT 总线登记和释放。后者主动构造真实 GT 主机对象的登记状态，未另造完整 GT 机器结构。

仓库存取、满容量拒收、迁移升级卡、1000 种 NBT 物品、UUID 冲突、文件缺失锁定、卸载和跨进程重启继续通过。总线真实 `getTexture` 返回对应连续面板切片，结构破坏后回到原生路径，未修改总线原材质 NBT；邻近非成员总线不换皮。

## 客户端边界

网页使用九张运行时切片，已检查完整机器与主机、接口、总线的组合外观。客户端 casing Mixin 的目标方法签名以字节码验证。公共静态初始化器不访问服务端会删除的客户端贴图字段，有专门回归测试。

未启动客户端进行 OpenGL、资源包、GT 染色与光照验收。浏览器为材质拼接预览，不代替客户端验收。GT 总线独占索引针对原生 `MTEMultiBlockBase.checkStructure` 登记入口；完全绕过该入口的第三方机器不在保证范围内。

## 证据

- [发行文件和检查清单](../../verification/modernization-0.2.2/manifest.json)
- [仓库](../../verification/modernization-0.2.2/vault/vault-tests.json) / [重启](../../verification/modernization-0.2.2/vault/vault-tests-restart.json)
- [锻炉](../../verification/modernization-0.2.2/forge/forge-power-tests.json) / [聚变](../../verification/modernization-0.2.2/fusion/fusion-power-tests.json)
- [AE](../../verification/modernization-0.2.2/ae/ae-order-report.json) / [结构操作](../../verification/modernization-0.2.2/structures/machine-validation/report.json)
- [结构与材质机制](VAULT_SMOOTH.md) / [图像生成记录](../assets/vault/formed-shell-generation.json)
- [0.2.1 历史报告](VALIDATION_0.2.1.md)

## English

The 0.2.2 artifact passed 76 unit tests, 243 dedicated-server checks and 10 structure-operation phases. Six test groups cover 300 actual shell layouts across all six directions, with each component visiting every remaining shell cell; this is not an exhaustive test of all position pairs. Shared buses/interfaces reject both vaults independently of check order and recover after separation. Native GT reservation/release is tested using real objects with deliberately constructed registration state. Production classes match the executed fixture. No game client was launched; client rendering and third-party machines bypassing native structure registration remain outside the validated scope.
