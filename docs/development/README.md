# 开发者文档 / Developer documentation

[中文首页](../../README.md) · [English README](../../README.en.md)

玩家操作说明位于 [功能指南](../features/AE_ORDERING.md)、[功率控制](../features/POWER.md) 与 [容量仓库](../features/STORAGE.md)。本目录集中构建、内部规则、实验、兼容性和验证边界。

## 当前整合版

- [2026-11-10 本地修正与验证](FEEDBACK_2026_11_10.md)：聚合器外观与网络统计、终端主题与原生拆卸、可点击配方报告。
- [构建与后台测试](BUILDING.md)：默认不启动游戏，整合夹具和可重复步骤。
- [模块与兼容迁移](INTEGRATION.md)：注册 ID、内部模块、库存文件、功率 NBT 和图标。
- [当前整合验证](VALIDATION.md)：0.3.0 / GTNH 2.9.0 的依赖、退回、后台测试和验证边界。
- [0.2.5 / RC-2 验证历史](VALIDATION_0.2.5.md)：此前动画界面的原始验证记录。
- [开发计划与调研](TODO.md)：已实现与待讨论功能分开记录。

## 结构终端与 AE

- [结构兼容性](STRUCTURE_COMPATIBILITY.md)
- [运行状态与结构检查](STRUCTURE_STATE.md)
- [结构验证历史](STRUCTURE_VERIFICATION.md)
- [AE 计算与发配内部规则](AE_ORDER_INTERNALS.md)
- [星门订单实验与耗时](AE_BENCHMARK.md)
- [完整整包逐机测试方法](../../verification/PACK_TESTING.md)
- [历史逐机结果](../../verification/machine-coverage.csv)
- [原料＋标记的后台验证](../../verification/ae-craftable-0.1.5-dev/README.md)

## 功率控制

- [聚变功率及热切换机制](FUSION_INTERNALS.md)
- [独立版测试方法](POWER_TESTING.md)
- [独立版验证历史](POWER_VALIDATION.md)
- [独立版聚变原始数据](../../verification/power-baseline/fusion-0.2.0/fusion-power-tests.json)

## 外部存储

- [主机原生动画界面与后台预览](VAULT_DASHBOARD.md)

- [原生优先级、Reshuffle 与主动输出](VAULT_IO.md)

- [整面蓝白紫材质、六方向与独占部件](VAULT_SMOOTH.md)

- [LuV 材质、配方和升级说明](VAULT_LUV.md)
- [贴图母图与生成提示词](../assets/vault/generation.json)

- [单聚合器查询与枚举优化](VAULT_READ_OPTIMIZATION.md)
- [读取边界审查与多聚合器设计](VAULT_READ_AUDIT.md)
- [读取缓存配置：用户指南](../features/CACHE_CONFIGURATION.md)
- [配置功能验证](VALIDATION.md)：小预算、关闭缓存与进程重启
- [BigInteger 库存数量预评估与性能](QUANTITY_PREFLIGHT.md)
- [BigInteger 容量与历史性能测量](CAPACITY.md)
- [存储格式、日志与恢复边界](STORAGE_FORMAT.md)
- [独立版验证历史](STORAGE_VALIDATION.md)
- [独立版原始报告](../../verification/storage-baseline/vault-tests.json)

历史记录不自动代表当前整合版的验证结果。当前结果以本目录的整合验证为准。客户端界面渲染与交互尚未启动游戏验收。

## English

This directory contains developer-facing build steps, internals, experiments, compatibility details and test evidence. Player-facing instructions remain in the READMEs and feature guides. Use the current [integration validation](VALIDATION.md) for the merged artifact; historical standalone reports retain their original scope. No game client has been launched for visual acceptance.

- [元件容量规则与贡献统计：当前验证](VALIDATION.md)（123 单元测试、302 后台检查、3 台结构样本；部分退回与重启恢复、AVS6）

- [集成样板实现与兼容边界](INTEGRATED_PATTERNS_FEASIBILITY.md)：36 槽容器、原生配方展开、接口终端、复制成本与后台验证。
