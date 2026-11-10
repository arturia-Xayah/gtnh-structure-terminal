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

当前路线遇到循环或多输出样板会停止，并在玩家聊天及服务器日志报告。未选择的其他路线不会阻断订单。“详细模式”仅报告主产物及其配方数，不改变选择顺序。环报告只显示成环的主产物链。带颜色、下划线的主产物可点击查看样板方块坐标；当前维度已加载位置高亮 30 秒，跨维度报告维度名及坐标。定位链接有效期为 10 分钟，移动样板后需重新计算获取新位置。无环、单输出仍可能因为缺少原料或样板而无法下单。

## 批量发配

无需倍增样板。CPU 会按库存、AE 能源、协处理器和接收端能力合并投料，一批最多 4096 份。批量投料不提高机器的物理并行。

原生 ME 接口可使用批量投料；第三方接收端需明确支持。工作台配方、阻挡模式及其他不适用的场景继续逐份发配。

目前路径检查会先展开所选样板图，因此即使部分中间材料已有库存，也可能继续检查其上游样板。遇到报告时请按提示修正相应路径。

## 装配线自动重命名

右键 ME 下单优化元件，开启“装配线自动重命名”（默认关闭）。在该网络的样板终端中从 NEI 导入装配线配方时，每种相同原料的第一份保留原名，第二、第三份依次使用固定名称 `重命名1`、`重命名2`。数量与输入位置不变；不同原料分别编号，流体和已有自定义名称的原料不改名。若目标名称已被同类原料占用，会跳过该编号。

名称在中英文客户端都相同。把成型机的名称模具命名为完全相同的 `重命名1`、`重命名2`，得到的物品就能匹配样板；此功能不会额外添加物品 NBT 标记。成型机仍需实际供货和加工，不会直接产生改名物品。

开启第二个开关“导入时生成改名样板”后，从 NEI 导入时就会额外生成缺少的 `1 个原料 → 1 个改名原料` 加工样板，放入玩家背包。主样板仍需正常点击编码。辅助样板不把常驻名称模具列为消耗材料；请将它们安装到使用对应名称模具的成型机接口。

每张辅助样板消耗一张普通空白样板，依次从终端空白样板槽、背包、AE 网络取用。网络提取遵守权限及耗电。背包空间或空白样板不足时取消本次导入，保留原配方；若网络实际提取少于预检数量，已取出的空白样板会留在终端空白样板槽，不生成半套样板。

自动去重检查当前网络可见的已安装样板、背包样板及终端样板输出槽。仅跳过输入、输出和 NBT 精确一致的单输入、单输出、1→1 加工样板；不会因为“这个产物可合成”就误跳过。未安装且存放在其他箱子或 ME 库存中的样板不在此次去重范围。关闭第二个开关时，只修改导入配方的重复输入，不生成辅助样板。

仅影响开关开启后的 NEI 装配线导入，不追溯修改已编码样板，也不修改其他机器配方或手动编辑。以样板终端当前连接的网络为准，不读取提供样板的其他网络的开关。

## 原料＋标记

独立于下单优化元件：原料在当前网络已有合成样板时显示＋。支持物品及流体。物品区分 metadata/NBT，流体匹配遵守该 AE 版本原生规则。

标记只表示样板存在，不能代替缺料检查。

## English

Craft the optimizer with gold/quartz/gold, redstone/crafting table/redstone, gold/quartz/gold. Connect one to the submitting grid; right-click for detail mode. It uses 1 AE/t and no channel. Merged-grid duplicates disconnect until the conflict is resolved.

An active optimizer selects the modified Lite planner and supported bulk dispatch. Without it, native planning and dispatch remain. The submitting grid governs cross-grid pattern borrowing. Selected-route cycles or multi-output patterns stop the order and report the issue. Detail mode reports main outputs and recipe counts without changing native route selection. Cycles display their main-output chain. Colored, underlined outputs link to provider coordinates and a 30-second outline in the current dimension; other dimensions report name and coordinates. Links expire after ten minutes; recalculate after moving patterns. Missing stock or patterns can still prevent crafting.

Keep unit patterns. Batches contain at most 4096 crafts and follow stock, energy, coprocessor and receiver limits. Native ME interfaces support batching; unsupported receivers, workbench recipes and blocking mode retain unit dispatch. Batching does not add processing parallelism.

The current validator expands the selected pattern graph before stock consumption, so it may inspect upstream patterns even for stocked intermediates.

The input **+** badge needs no optimizer. It indicates a pattern in the current grid, not enough stock for every dependency. Item identity preserves metadata/NBT; fluids follow this AE version's native matching.

**Assembly-line auto-renaming:** enable the optional switch in the optimizer (off by default). NEI assembly-line imports retain the first occurrence of each exact item and name later occurrences `重命名1`, `重命名2`, etc. Different items number independently; counts/positions, fluids and existing custom names are preserved. Occupied names are skipped. Names are fixed across client languages and match a Forming Press using a Name Mold with that exact name. The second, default-off switch creates missing 1:1 renaming processing patterns immediately on import and delivers them to your inventory. The main pattern is still encoded normally. Blanks are consumed from the terminal slot, your inventory, then the powered ME network. Install the auxiliary patterns at Forming Presses with matching Name Molds; molds are not consumed recipe inputs. Exact unit processing patterns already installed in the current crafting grid, carried in your inventory or in the terminal output slot are skipped. Loose patterns in other containers/ME storage are not scanned. Insufficient space or blanks cancels the import without replacing the old matrix; a partial network extraction is retained as blank patterns in the terminal slot. The terminal's own active grid controls this feature; existing patterns, manual edits and other recipe maps are unaffected.

[开发与内部实现 / Development](../development/README.md)
