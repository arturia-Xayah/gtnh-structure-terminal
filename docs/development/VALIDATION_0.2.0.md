# 0.2.0 整合验证 / Merged validation

日期：2026-10-22。目标：GTNH 2.9.0-RC-2 / GT5 5.09.54.205 / AE2 rv3-beta-1080-GTNH。所有服务器使用 Java 25.0.4.1，以 `nogui` 和 `java.awt.headless=true` 运行；没有启动游戏客户端。

## 结果

| 范围 | 结果 |
|---|---|
| 合并后的单元测试 | **72 项通过** |
| 容量仓库首次运行 | **22 项通过** |
| 容量仓库独立进程重启 | **4 项通过** |
| 锻炉回归 | **26 项通过** |
| 普通及压缩聚变 | **126 项通过** |
| AE 下单、发配及原料标记查询 | **51 项通过** |
| 结构终端 | **3 台机器、10 个操作阶段通过**，包含搭建、部分已有结构、替换和拆除 |
| 四模块共同加载 | 六个服务器进程均确认旧注册 ID、统一版本、显示名称、父模块及终端图标 |
| 正式发行校验 | Java 8 字节码、正确运行时映射、无测试类，生产 class 与两份实际测试夹具逐字节一致 |

共重新执行 229 项非结构后台检查，以及三个控制器的 10 个结构阶段。结构样本为 ID 141、1000、15750；所有阶段材料账平衡，原主机保留，预览状态检查通过。没有将历史全机覆盖报告视为本次所有机器都已重测。

仓库确认真实非空 AE 物品元件、AE2FC 流体元件及升级卡迁移、共享容量限制、1000 种不同 NBT 物品、重复 UUID 锁定、缺失文件拒绝、卸载/重放和跨进程重启。锻炉确认运行实际配方、颜色预算、公共原料、换仓、配置复制及数据宝珠。聚变覆盖十台普通/压缩 Mk-I～Mk-V，并以实际 tick 验证低→高→低配方衔接。

AE 套件保留网络按元件启用逻辑，验证循环/多输出报告、子网来源、批量事务、回退与原料＋标记的同步数据查询。界面渲染与客户端 Mixin 执行不由这些后台用例替代。

## 原始记录与安装包

[校验清单、SHA-256 与运行时 class 哈希](../../verification/modernization-0.2.0/manifest.json)

- [仓库](../../verification/modernization-0.2.0/vault/vault-tests.json) / [仓库重启](../../verification/modernization-0.2.0/vault/vault-tests-restart.json)
- [锻炉](../../verification/modernization-0.2.0/forge/forge-power-tests.json)
- [聚变](../../verification/modernization-0.2.0/fusion/fusion-power-tests.json)
- [AE](../../verification/modernization-0.2.0/ae/ae-order-report.json)
- [结构逐阶段](../../verification/modernization-0.2.0/structures/machine-validation/report.json)

两份夹具仅测试入口隔离方式不同；所有生产 class 均与发行包一致。首次合并运行暴露了历史仓库夹具未正确禁用其他套件入口的问题，修复在测试源集中；没有修改仓库格式、容量算法或功率规则。最终回归分别在新的独立世界运行，仓库重启仅复用自己的测试世界。

## 边界

本次不包含所有 GTNH 控制器、所有机器尺寸/模式、全部第三方元件、真实断电或任意部分存档回滚。结构测试不等于全部机器运行配方测试。聚变的配置 NBT/复制与连续衔接已覆盖，但未新增聚变跨进程运行中恢复或移除 Mod 测试。GUI 的服务器构造、按钮权限和固定字节码选择器已检查；客户端画面、鼠标和缩放验收仍独立。

## English

The merged artifact passed **72 unit tests**, **229 non-structure dedicated-server cases**, and **10 structure-operation phases across three controllers**. Six JVM processes confirmed all four legacy namespaces, one version, unified display names, parent metadata and the approved terminal icon. Production classes match both executed fixture artifacts byte-for-byte. The client was never launched. This is targeted integration regression, not a fresh sweep of every controller, recipe, dimension or third-party cell. Raw reports and scope limits are linked above.
