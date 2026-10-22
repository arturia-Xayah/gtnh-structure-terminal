# TODO / 待办

## 已确认 / Confirmed

- [x] **AE 样板输入的可合成标记 / Craftable indicators on pattern inputs**
  - 输入原料在当前终端网络中有有效合成样板时，在右上角显示 `+`，悬浮提示“网络中已有合成样板”。库存有料时仍显示；该标记不代表整条依赖链原料充足。
  - 复用 AE2 已同步的、未经过搜索或显示筛选的库存记录及 craftable 标志；使用 AE 原生 `findPrecise`，不逐帧扫描全网样板。物品按元数据与 NBT 匹配；此版本的 `AEFluidStack.create` 忽略自定义流体 NBT，标记遵守相同规则。
  - 覆盖原生、扩展与共用原生界面的无线样板终端；只标记输入槽，不标记输出槽，不修改样板内容。移除样板、网络更新和切换终端后不能保留过期标记。
  - 独立的客户端显示功能；与 ME 下单优化元件的计算、派发开关分离。客户端类不得被后台服务器加载。
  - Use the terminal's synchronized, unfiltered craftable state and native precise lookup. Items preserve metadata/NBT; this pinned AE version discards custom fluid NBT. Mark stocked inputs too; never alter pattern contents or imply full-chain availability. Keep client classes safe for dedicated servers.
  - 状态 / Status：已在 0.1.5-alpha 开发源码实现；[33 项自动检查和 51 项后台用例通过](../../verification/ae-craftable-0.1.5-dev/README.md)，客户端 Mixin 与画面尚未启动验证。Implemented and checked headlessly; client Mixin execution and visual validation remain pending.

## 调研候选 / Research candidates

以下只是待讨论的建议，不代表已决定实现。These are proposals, not scheduled features.

| 候选 / Candidate | 范围 / Scope |
|---|---|
| 编码后选择目的地并发送 / Encode and send | 消耗真实空白样板，遵守网络权限、空位与可见性；提示相同主产物已有样板。Consume a real blank pattern and respect access, capacity and visibility. |
| 样板配方信息 / Pattern provenance | 显示来源配方、机器、电压与编码者；仅提供信息，不绕过研究或机器条件。Display-only recipe, machine, voltage and author information. |
| 清除样板副产物 / Clear secondary pattern outputs | 只改变 AE 对完成产物的声明；不删除机器实际副产物，不解决满罐，也不保证随机主产物能按固定数量完成。Only changes the pattern declaration, never physical outputs. |
| 按结构部件备料 / Prepare materials by structure piece | 如神锻只选择第三环，独立统计材料并向 AE 请求现有方块或其正常合成。保留原机解锁、建造、耗材与成型规则。Select a piece such as Godforge ring 3 and request its actual construction materials. |

## 源码核对 / Source notes (2026-10-21)

- GTO 的 [样板编码菜单](https://github.com/GregTech-Odyssey/GTOCore/blob/main/src/main/java/com/gtocore/mixin/ae2/menu/PatternEncodingTermMenuMixin.java) 提供发送目的地、空位检查、相同主产物提示、空白样板扣除与失败回退。
- GTO 的 [配方转移入口](https://github.com/GregTech-Odyssey/GTOCore-Main/blob/main/src/main/java/com/gtocore/integration/emi/GTAe2PatternTerminalHandler.java) 将多方块配方交给结构编码器。不能仅凭此入口断言 GTO 对 GTNH 神锻有第三环专用适配。
- GTNH 2.9.0-RC-2 所用本地 GT5 源码（5.09.54.205）在 `MTEForgeOfGods` 中分别定义 `first_ring`、`second_ring`、`third_ring`，有独立偏移与构建调用，因此第三环独立统计可实现。运行中的渲染机制可能将实体环替换为空气，不能仅按当前世界中缺失的方块判断应补料；应先用静态结构部件定义统计，并保留原机状态与解锁检查。
- 当前发布的结构终端尚无 AE 样板编码功能；上述分环方案需要新增适配。把完整三环清单和两环清单按材料相减，只能作为回退方案，并须验证位置、材料选择、共享部分及升级时替换的方块。
- [GTNH 原生样板终端](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial/blob/rv3-beta-1080-GTNH/src/main/java/appeng/client/gui/implementations/GuiPatternTerm.java) 已支持倍增、除法和专用倍率界面；不将其当作 GTO 独有功能重复移植。
- 结构环不是可由机器直接产出的普通物品。备料应请求实际方块；如未来使用虚拟材料包样板，必须另外定义完成回执，否则 AE 会一直等待虚构产物。

No game client was launched. The confirmed input-badge feature is implemented; the remaining candidates are research proposals only.
