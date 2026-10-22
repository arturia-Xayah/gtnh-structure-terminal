# AE 下单优化 / AE order optimization

[中文首页](../../README.md) · [English README](../../README.en.md)

## 元件与使用

ME 下单优化元件的合成配方：

```text
金锭  石英    金锭
红石  工作台  红石
金锭  石英    金锭
```

连接到下单所用的网络，右键可开启“详细选路报告”。不占频道，待机耗电 1 AE/t，每个网络最多一个。网络合并后多出的元件会断开并提示冲突，移除冲突后尝试恢复连接。

有效元件存在时，网络使用改造后的 Lite 下单计算和支持接收端上的批量发配。没有有效元件时保留 AE 原本选定的模式。子网使用其他网络样板时，以下单网络为准。

当前路线遇到循环或多输出样板会停止，并在玩家聊天及服务器日志报告。未选择的其他路线不会阻断订单。“详细模式”额外列出同产物的候选配方及最终选择，不会改变选择顺序。无环、单输出仍可能因为缺少原料或样板而无法下单。

## 批量发配

无需倍增样板。CPU 会按库存、AE 能源、协处理器和接收端能力合并投料，一批最多 4096 份。批量投料不提高机器的物理并行。

原生 ME 接口可使用批量投料；第三方接收端需明确支持。工作台配方、阻挡模式及其他不适用的场景继续逐份发配。

目前路径检查会先展开所选样板图，因此即使部分中间材料已有库存，也可能继续检查其上游样板。遇到报告时请按提示修正相应路径。

## 原料＋标记

独立于下单优化元件：原料在当前网络已有合成样板时显示＋。支持物品及流体。物品区分 metadata/NBT，流体匹配遵守该 AE 版本原生规则。

标记只表示样板存在，不能代替缺料检查。

## English

Craft the optimizer with gold/quartz/gold, redstone/crafting table/redstone, gold/quartz/gold. Connect one to the submitting grid; right-click for detail mode. It uses 1 AE/t and no channel. Merged-grid duplicates disconnect until the conflict is resolved.

An active optimizer selects the modified Lite planner and supported bulk dispatch. Without it, native planning and dispatch remain. The submitting grid governs cross-grid pattern borrowing. Selected-route cycles or multi-output patterns stop the order and report the issue. Detail mode lists alternatives without changing native route selection. Missing stock or patterns can still prevent crafting.

Keep unit patterns. Batches contain at most 4096 crafts and follow stock, energy, coprocessor and receiver limits. Native ME interfaces support batching; unsupported receivers, workbench recipes and blocking mode retain unit dispatch. Batching does not add processing parallelism.

The current validator expands the selected pattern graph before stock consumption, so it may inspect upstream patterns even for stocked intermediates.

The input **+** badge needs no optimizer. It indicates a pattern in the current grid, not enough stock for every dependency. Item identity preserves metadata/NBT; fluids follow this AE version's native matching.

[开发与内部实现 / Development](../development/README.md)
