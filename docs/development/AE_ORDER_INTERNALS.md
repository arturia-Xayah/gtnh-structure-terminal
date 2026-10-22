# ME 下单优化元件 / ME Order Optimizer

本模块从 Structure Terminal **0.1.4-alpha** 起内置，客户端与服务端只安装合并后的结构终端 JAR。移除独立的 AE Order Optimizer JAR，避免重复 Mod ID。保留 `aeorderoptimizer:core` 及 TileEntity ID，便于已有存档继续识别。当前整合版将所有功能归入 GTNH现代化升级，保留历史注册模块 ID 以兼容存档。

Bundled since Structure Terminal **0.1.4-alpha**. Install only the merged Structure Terminal JAR on both sides and remove the standalone AE Order Optimizer JAR to avoid duplicate Mod IDs. Block and TileEntity registration IDs are retained for existing saves.

## 使用 / Usage

1. 合成并将 **ME 下单优化元件** 接入下单网络，耗电 1 AE/t，不占用频道。
2. 右键打开 GUI，设置“详细选路报告”。默认关闭，设置随方块保存。
3. 正常下单。有有效元件时，无论终端此前选择普通还是 Lite，都进入本 Mod 的严格 Lite 路径；没有元件时保留原版所选模式和派发逻辑。

Craft and connect one **ME Order Optimizer** to the submitting grid (1 AE/t, no channel). Right-click for the detailed-report toggle, off by default and saved with the block. Place orders normally. An active optimizer selects the strict Lite planner; without it the original selected planner and dispatcher remain in use.

合成配方 / Crafting recipe:

```text
金锭  石英    金锭       Gold   Quartz  Gold
红石  工作台  红石       Redstone Table Redstone
金锭  石英    金锭       Gold   Quartz  Gold
```

每个网络最多接入一个元件。第二个不能直接放在同网设备旁；两个独立网络合并时，按维度和坐标稳定保留一个，其余元件断开网络并在 GUI 显示冲突。不会删除方块；移除冲突后会尝试重新连接。

Only one optimizer may connect to a grid. Duplicate adjacent placement is rejected. When grids merge, dimension/coordinate ordering keeps one active and disconnects the others, showing the conflict in their GUI. Blocks are retained and retry connection after the conflict is removed.

子网借用其他网络样板时，**以下单网络为准**，不是样板来源或 CPU 所在网络。跨网络提交的订单来源随 CPU 保存；不同来源合并到同一 CPU 的订单保守使用原生逐份派发。

The **submitting grid** controls opt-in, including borrowed patterns, regardless of pattern-provider or CPU ownership. Cross-grid order origin is persisted with the CPU. Merged jobs from different origins use native dispatch conservatively.

## 计算与报告 / Planning and reports

- 只展开 Lite 原本选中的依赖路线；不会重新排序候选或缺料换路。
- 选中路线遇到自循环、多节点循环或多输出样板，立即中止，在服务器日志和下单玩家聊天中打印问题。错误报告不受详细开关影响。
- 无关样板、未选中的候选即使有环或多输出，也不会阻断本单。
- 详细模式列出当前路径产物的已注册配方，并标记 `[SELECTED]`。原版候选表可能过滤同优先级配方；报告读取注册表，不改变实际选择。
- 长报告完整写入服务器日志，玩家聊天只显示长度受限的摘要。
- 材料身份包含物品/流体类型与 NBT。单种产物的数量大于 1 仍是单输出。
- 本版在原有同步调用流程中计算，没有新增后台计算线程。

Only the route selected by native Lite is expanded. No new candidate ranking or missing-material route fallback is introduced. Selected self-cycles, multi-node cycles and multi-output patterns stop planning with an error printed to the server log and requesting player's chat, regardless of detail mode. Unrelated or unselected invalid patterns do not block the order. Detail mode lists registered alternatives and marks `[SELECTED]`, without changing the native candidate list. Material identity includes type and NBT. Planning remains synchronous.

无环、单输出并不保证一定有料：缺失原料、没有生产样板或 Lite 选中的路线缺料，仍会显示缺料。

A valid acyclic, single-output route may still lack stock or a production pattern; native Lite's fixed route can also be unavailable when an alternative exists.

当前限制：路径检查先展开选定样板图、再扣库存，可能检查已有充足库存材料的生产候选。库存感知剪枝尚待改进；星门实测中已记录这个边界。

Current limitation: validation expands the selected pattern graph before consuming simulated stock, so it can inspect upstream patterns for already-stocked materials. Inventory-aware pruning remains an improvement item, documented in the Stargate benchmark.

## 批量派发 / Bulk dispatch

样板保持每份原始数量。CPU 根据剩余份数、库存、AE 能源、协处理器数和接收端能力决定批量；单批最多 4096 份、每个 CPU 每 tick 最多 64 个批量事务。同配方的多个接收端按批次轮转。一次成功批量只扣一次派发操作，剩余配方份数和预期产物按真实数量扣加。

Patterns retain their unit quantities. The CPU chooses a batch from remaining crafts, inventory, AE power, coprocessors and receiver capability, capped at 4096 crafts per batch and 64 bulk transactions per tick. Multiple receivers are served in round-robin batches. Each accepted batch costs one dispatch operation while craft counts and expected outputs use the actual quantity.

原生 ME 接口支持直接批量投料；部分接收时沿用原生接口的待发送队列。其他处理器可实现 `IBatchMedium` 明确声明容量与批量接收协议。拒收会退回本批材料，最多再尝试一份，避免无限缩批重试。

Native ME interfaces support bulk inventory insertion and retain their original pending-input queue for partial reception. Third-party processors can implement `IBatchMedium` to advertise batch capacity and acceptance. A rejected batch is returned and may be retried once at size one.

工作台合成、模糊替代、输入专用样板、假合成及未声明批量能力的第三方接收器使用原生逐份派发。传统 `ICraftingMachine` 接收器不接收放大的计划。阻挡模式保持单份，智能阻挡仍由接口原逻辑判定。批量投料不增加机器自身并行。

Workbench crafting, fuzzy substitution, input-only patterns, fake crafting and third-party media without batch support retain native per-craft dispatch. Legacy `ICraftingMachine` plan consumers receive unit plans. Blocking mode remains unit-sized; smart blocking uses original interface rules. Dispatch batching does not increase machine processing parallelism.


[星门测量 / Stargate measurements](AE_BENCHMARK.md) · [合并验证 / Merge validation](../../verification/merged-0.1.4/README.md)
