# 锻炉与聚变功率控制 / Forge and fusion power controls

[中文首页](../../README.md) · [English README](../../README.en.md)

## 设置功率

适用：超维度等离子锻炉、普通聚变 Mk-I～Mk-V、压缩聚变 Mk-I～Mk-V。

1. 打开原主机界面，点击蓝色 **P**。
2. 选择输入仓、总线或输入总成的染色颜色。16 色与无色组分别保存设置。
3. 输入最大 A 数，选择 **ULV～MAX** 电压单位，保存设置。

`0` / `-1` 代表自动。支持小数 A；换算后向下取整到整数 EU/t。电压选项是功率换算单位，不会改变机器或能源仓的等级。能源仓不必染色。

| 输入组 | 示例 | 用途 |
|---|---|---|
| 红色 | 1A UEV | 限制低功率配方使用的超频预算 |
| 绿色 | 16A UMV | 为其他配方允许更大预算 |
| 无色 | -1 | 遵守原版处理功率规则 |

上限不是固定耗电值：实际耗电仍由配方、超频和适用的并行决定。面板同时显示 EU/t 及等效 A 数、电压；很大的数值采用科学计数法，悬停可看精确值。

设置在下一轮配方生效，保存不会重算当前配方。未成型时保留配置；更换能源仓后成功成型时，超过当前能力的手动上限会下调并保存，之后换回大能源仓不会自行提高。

## 输入颜色

遵守原生染色和输入分离规则，同一输入总成的内部槽使用总成本体颜色。无色公共原料仍可按原机规则补充有色组。完全由无色原料满足的配方使用无色预算，不能借空的有色组绕过上限。

只限制运行预算，不将能源仓分成独立电源，也不会让一台机器同时运行多个不同配方。能源不足、缺料或输出受阻仍会按原机规则停机。

## 聚变的连续运行

启动能量 **EU** 与运行功率 **EU/t** 分别计算。降低功率上限不会降低开机能量门槛，也不会使低等级聚变运行更高等级的配方。

保留原版选配方顺序、缓存及完成后衔接下一配方的机制，不新增“下单优先”。如果常驻配方一直有料，它可能继续被选中；请使用 AE 投料控制所需原料。预算低于配方基础耗电时不会强行降耗运行。

## 复制与保存

- 物质操纵者可以通过原生 GT 配置复制路径保留设置。
- 数据宝珠：左键主机保存配置，右键目标主机粘贴。
- 复制设置，不复制配方进度、物品或能源。目标成功成型后校验其上限。
- 原有功率设置及电压索引继续兼容，无需重新设置所有颜色。

## English

Open the existing controller, click **P**, select the input color, enter maximum amperage and a voltage unit, then save. Supported: DTPF, ordinary Fusion Mk-I–V and Compact Fusion Mk-I–V. **0 / -1** means automatic. Fractional amperage is supported and converted conservatively to whole EU/t. Voltage is a display/conversion unit; it does not alter machine or hatch tier.

Colors and the unpainted group have independent budgets. Internal slots use their input assembly's color. Native shared unpainted ingredients and separation rules remain; recipes fully satisfied by unpainted ingredients use the unpainted budget. Energy hatches do not need colors. Limits do not create isolated power supplies or simultaneous different recipes.

Edits apply to the next recipe. Re-forming after supply changes clamps excessive manual limits; incomplete structures retain settings. Native energy failures, ingredient shortages and blocked outputs still stop machines normally.

Fusion startup EU is separate from processing EU/t. Startup eligibility, native recipe order, caches and continuous handoff remain; no order-priority scheduler is added. Change ingredient availability through AE to select a different recipe. Budgets below base recipe EU/t cannot run it by underclocking.

Matter Manipulator copies settings through GT's normal configuration path. Data Orb: left-click to capture, right-click to paste. Only settings are copied; no energy, stock or progress. Previous settings and voltage IDs remain compatible.

[开发文档与验证 / Development and validation](../development/README.md)
