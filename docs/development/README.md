# 开发者文档 / Developer documentation

[中文首页](../../README.md) · [English README](../../README.en.md)

玩家操作说明位于 [功能指南](../features/AE_ORDERING.md)、[功率控制](../features/POWER.md) 与 [容量仓库](../features/STORAGE.md)。本目录集中构建、内部规则、实验、兼容性和验证边界。

## 当前整合版

- [构建与后台测试](BUILDING.md)：默认不启动游戏，整合夹具和可重复步骤。
- [模块与兼容迁移](INTEGRATION.md)：注册 ID、内部模块、库存文件、功率 NBT 和图标。
- [当前整合验证](VALIDATION.md)：本次整合实际执行的检查、结果和限制。
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

- [原生优先级、Reshuffle 与主动输出](VAULT_IO.md)

- [整面蓝白紫材质、六方向与独占部件](VAULT_SMOOTH.md)

- [LuV 材质、配方和升级说明](VAULT_LUV.md)
- [贴图母图与生成提示词](../assets/vault/generation.json)

- [存储格式、日志与恢复边界](STORAGE_FORMAT.md)
- [独立版验证历史](STORAGE_VALIDATION.md)
- [独立版原始报告](../../verification/storage-baseline/vault-tests.json)

历史记录不自动代表当前整合版的验证结果。当前结果以本目录的整合验证为准。客户端界面渲染与交互尚未启动游戏验收。

## English

This directory contains developer-facing build steps, internals, experiments, compatibility details and test evidence. Player-facing instructions remain in the READMEs and feature guides. Use the current [integration validation](VALIDATION.md) for the merged artifact; historical standalone reports retain their original scope. No game client has been launched for visual acceptance.
