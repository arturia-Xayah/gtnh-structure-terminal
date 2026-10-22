# 0.2.1 LuV 仓库整合验证 / LuV vault validation

日期：2026-10-22，目标 GTNH 2.9.0-RC-2。Java 25.0.4.1，全部服务器以 `nogui` / `java.awt.headless=true` 启动，未启动游戏客户端。

| 范围 | 结果 |
|---|---|
| 单元测试 | **72 项通过** |
| LuV 仓库首次运行 | **26 项通过** |
| 仓库独立进程重启 | **4 项通过** |
| 锻炉 | **26 项通过** |
| 普通与压缩聚变 | **126 项通过** |
| AE 下单、发配与原料标记数据查询 | **51 项通过** |
| 结构终端 | **3 台控制器、10 个操作阶段通过** |
| 成型外观 | 真实 GT 总线返回 LuV 基础材质；拆开恢复原路径；未修改原材质 NBT |
| 无关机器 | 邻近但不属于仓库的 GT 输入总线保留原外观 |
| 制作门槛 | 两条组装机配方均为 30,720 EU/t，无工作台后备配方 |
| 结构材料 | 原生 LuV 外壳成型；ULV、IV、ZPM 和旧版外壳均拒绝 |
| 发行文件 | Java 8 字节码，无测试入口；所有生产 class 与实际测试夹具逐字节一致 |

本次重新执行 233 项非结构后台检查和 10 个结构阶段。使用同一份整合夹具，所有四模块同时加载；六次服务器进程正常退出，仓库第二次仅复用自己的测试世界。各套原有配方处理、存取、迁移、颜色、热切换、复制、AE 下单与结构材料账检查继续通过。

## 新增检查

仓库中检查两条组装机配方的输出、输入、流体和 LuV EU/t，并确认没有工作台配方。将结构的一个外壳改为其他等级或旧版外壳，验证不能成型；恢复原生 LuV 外壳后 AE 可再次访问。

通过实际 `MTEHatch.getTexture` 调用验证材质补丁已加载，成型总线得到 LuV 基础材质，结构被破坏后回到原有显示，前后存档材质字段一致。另在仓库旁放置非成员总线验证不会错误换皮。仓库数据继续覆盖非空元件迁移、升级卡、1000 种 NBT 物品、满容量拒收、重复 UUID、缺失文件锁定、卸载重放和跨进程重启。

网页预览直接使用发行用的两张 64×64 RGBA 贴图及本地 GT LuV 贴图，已实际打开并检查成型开关、旋转和显示。网页光照、GT 染色等为近似，不把它作为 Minecraft 客户端 OpenGL 渲染验收。

## 报告

- [安装包、生产 class 哈希及检查清单](../../verification/modernization-0.2.1/manifest.json)
- [LuV 仓库](../../verification/modernization-0.2.1/vault/vault-tests.json) / [重启](../../verification/modernization-0.2.1/vault/vault-tests-restart.json)
- [锻炉](../../verification/modernization-0.2.1/forge/forge-power-tests.json)
- [聚变](../../verification/modernization-0.2.1/fusion/fusion-power-tests.json)
- [AE](../../verification/modernization-0.2.1/ae/ae-order-report.json)
- [结构阶段](../../verification/modernization-0.2.1/structures/machine-validation/report.json)
- [材质、配方与旧结构升级机制](VAULT_LUV.md)
- [0.2.0 整合验证历史](VALIDATION_0.2.0.md)

## 边界

未启动客户端验证方块渲染器、GUI、资源包兼容性和光照。后台仅验证形成/失效时返回的真实 GT 材质及补丁边界。结构样本不等于逐台运行所有 GTNH 机器，仍不包含全部尺寸、模式、配方、第三方元件或真实断电故障注入。外部文件格式与已有迁移边界不变。

## English

The 0.2.1 artifact passed 72 unit tests, 233 dedicated-server cases and 10 structure phases across three controllers. New checks cover LuV assembler gates, exact casing tiers, live hatch-skin injection, native fallback on invalidation and no changes to unrelated buses or saved hatch textures. A second process restores the vault. The production artifact matches the executed fixture's classes. The browser preview uses the actual runtime overlays; no game client was launched, so client OpenGL/resource-pack rendering remains unverified.
