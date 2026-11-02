# 0.2.6 · GTNH 2.9.0 正式版兼容验证

日期：2026-11-02。目标 Minecraft 1.7.10 / GTNH 2.9.0 正式版；Java 25.0.4.1。仅运行独立服务器，使用 `nogui` 与 `java.awt.headless=true`，未启动游戏客户端。

## 本次更新

按 [官方 2.9.0 清单](https://github.com/GTNewHorizons/DreamAssemblerXXL/blob/1b62ef00ad679c51ab0ec67f8a8e42778a059ce6/releases/manifests/2.9.0.json) 更新编译和测试依赖，并下载官方 Java 17–26 服务器 ZIP 实测。

| 依赖 | RC-2 实测运行版本 | 2.9.0 正式版 |
|---|---|---|
| GregTech | 5.09.54.205 | 5.09.54.265 |
| AE2 | rv3-beta-1080-GTNH | rv3-beta-1095-GTNH |
| StructureLib | 1.4.45 | 1.4.47 |
| ModularUI2 | 2.3.92-1.7.10 | 2.3.94-1.7.10 |
| GTNHLib | 0.11.52 | 0.11.54 |
| NEI | 2.8.155-GTNH | 2.8.171-GTNH |

旧编译配置中 NEI 为 2.8.110；本次与实际运行版本统一。每套后台夹具直接断言以上六个运行时依赖的版本，避免误用 RC-2 的服务器。服务器 ZIP 和依赖 JAR 哈希见 [依赖清单](../../verification/modernization-0.2.6/dependency-update.json)。

上游 AE 更新包含同优先级样板保留、CPU 取消/完成状态及库存观察者修复。本项目通过原有注入点接入，没有覆盖这些上游方法；未安装优化元件的网络继续使用上游计算与发配。原料＋标记的分配点、字段和提示方法以新 AE 字节码验证；锻炉/聚变选择器与原生外壳渲染入口以新 GT 字节码验证。

生产代码无需额外算法补丁。注册 ID、世界文件路径、功率设置 NBT 保持不变，仓库核心/同步类与 0.2.4 逐字节一致。保留 [0.2.5 动画界面验证](VALIDATION_0.2.5.md) 的历史边界。

## 本轮执行结果

| 范围 | 结果 |
|---|---|
| 单元测试 | **94 项通过** |
| 优先级、Reshuffle 与输出 / 重启 | **14 / 2 项通过** |
| 仓库存取 / 重启 | **38 / 4 项通过** |
| 锻炉功率控制 | **26 项通过** |
| 普通与压缩聚变 | **126 项通过** |
| AE 下单、批量发配与原料标记 | **51 项通过** |
| 结构终端 | **3 台主机、10 个操作阶段通过** |
| 0.2.5 旧世界升级 / 再次重启 | **4 / 4 项通过** |
| 玩家安装包 | **145 个 Java 8 生产类；与已执行夹具逐字节一致** |

共 **261 项本轮模块后台检查 + 8 项旧世界升级/重启检查**，另有 10 个结构操作阶段。AE 同时验证未安装优化元件时保留原版行为，以及安装后的路径校验、路线诊断、跨网络来源、批量接收与数量守恒。所有进程正常退出；运行日志未发现 Mixin 应用失败或 NoSuchMethodError / NoSuchFieldError。

## 存档升级

从此前 **0.2.5 / GTNH 2.9.0-RC-2** 的独立测试世界复制世界目录和重启标记，在正式版服务器上加载，再在独立进程中重启。核对 UUID、容量、1000 种库存、物品/流体数量和 AE 网络重连。仅修改测试副本，未修改原世界；这不等于所有玩家世界或其他附属 Mod 的迁移验收。升级仍应停服并备份整个世界。

## 范围与边界

使用同一测试 JAR 的生产 class 与玩家 JAR 逐字节比对，玩家 JAR 不含测试入口或依赖类。四个旧模块 ID、共同版本和终端图标一致。后台测试验证服务器逻辑；没有启动客户端验证 GPU、资源包、字体、鼠标交互和 GUI 实际缩放。客户端关键注入点的字节码检查不代替客户端视觉验收。结构操作仍是三台主机的样本，不代表逐台验证全部 GTNH 多方块。

## 证据与重现

- [正式包、单元测试与运行清单](../../verification/modernization-0.2.6/manifest.json)
- [官方整合包清单副本](../../verification/modernization-0.2.6/official-manifest.json) · [依赖和 ZIP 哈希](../../verification/modernization-0.2.6/dependency-update.json)
- [存储器](../../verification/modernization-0.2.6/vault/vault-tests.json) · [输出](../../verification/modernization-0.2.6/vault_io/vault-io-tests.json)
- [锻炉](../../verification/modernization-0.2.6/forge/forge-power-tests.json) · [聚变](../../verification/modernization-0.2.6/fusion/fusion-power-tests.json)
- [AE](../../verification/modernization-0.2.6/ae/ae-order-report.json) · [结构操作](../../verification/modernization-0.2.6/structures/machine-validation/report.json)
- [旧世界升级及重启](../../verification/modernization-0.2.6/upgrade/summary.json)

按 [构建与后台测试](BUILDING.md) 执行，传入官方 2.9.0 服务器目录；运行器默认 `--pack-version 2.9.0`。用 `run_vault_upgrade.py` 的 `--old-run` 指向此前 vault 测试目录。验证器接受 `--version 0.2.6-alpha --target 'GTNH 2.9.0'`，并检查运行器声明、实际依赖和夹具哈希。

## English

Version 0.2.6-alpha pins dependencies to the official GTNH 2.9.0 stable manifest. Tests use a fresh official dedicated-server pack; every suite asserts all six dependency versions. Existing production storage/sync bytecode and save identifiers are preserved. A copy of the previous 0.2.5 / RC-2 vault world is loaded and restarted under the new pack. See the results above and linked evidence. No game client or GPU acceptance was performed, and the three structure samples do not cover all multiblocks.
