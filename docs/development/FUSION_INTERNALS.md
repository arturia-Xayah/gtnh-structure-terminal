# 聚变与压缩聚变 / Fusion support

0.2.0-alpha，目标 GTNH 2.9.0-RC-2 / GT 5.09.54.205。保留原控制器与注册 ID。

## 使用

1. 普通聚变 Mk-I～Mk-V、压缩聚变 Mk-I～Mk-V 均在主机原生界面增加 **P** 按钮。原锻炉继续使用相同入口。
2. 按输入仓、输入总线、输入总成的颜色设置功率；无色是独立组，不是全局上限。`0` / `-1` 为自动。
3. 电压选项扩展至 ULV～MAX，包含 LuV、ZPM、UV。电压是 A 数换算单位，不改变机器等级或物理电压。
4. 配置只在下一轮配方计算时生效；保存不停止、不重启当前反应。配置可通过原有 IDataCopyable 路径与数据宝珠补丁复制。

例如，在 Mk2 普通聚变中，把无色普通配方设为 `1A LuV = 32,768 EU/t`，有色下单配方设为自动或更高预算。基础 EU/t 不高于预算的配方，按预算限制其超频；压缩聚变还会限制原生并行。不保证每条配方恰好耗到上限，也不允许低于配方基础耗电强行运行。

**保留原版选配方顺序。** 不添加“下单优先”调度，不重排颜色、总成、配方或缓存。如果常驻配方一直满足原料条件，它可能持续被选中；切换需通过现有投料自动化控制原料。限制导致某候选无法运行时，仍由原生遍历决定后续候选。

普通仓室保留无色公共原料规则。若某配方仅使用无色原料就能完整执行，按无色预算计算，避免借一个空的有色组抬高预算。输入总成使用总成本体颜色，内部配方槽仍按原生顺序遍历。

## 启动与热切换

- 启动能量 **EU** 与运行功率 **EU/t** 分开。设置运行功率不会降低启动能量门槛、改变反应堆容量，或让 Mk1 机器运行 Mk2 配方。
- 保留原生 `onPostTick`、扣电、产物输出与完成后立即检查下一配方的流程。前一轮结束时能找到下一条有效配方，才保留连续运行路径；中途缺料、输出受阻或停机后，按原版重新启动。
- 功率面板在聚变中显示的是**原生处理功率上限**。普通聚变为本机等级 1A；压缩聚变按其原生并行处理能力计算。它不是实时供电功率，也不是当前能源仓缓存。
- 保留原生能源仓充电速率和缓存。低功率运行时可积累能量，高功率运行仍可能耗尽缓存并按原版停机。
- 存档额外保留当前反应使用的功率预算，以免重载时用刚修改、尚未生效的设置重算旧反应；配置复制只复制设置，不复制进度、能量或运行状态。

## 兼容性

原有电压索引 0～4（UEV～MAX）保持不变，新电压单位追加索引；旧存档与原锻炉配置可继续读取。旧版 Mod 不认识新增单位，请客户端与服务端一起更新，移除旧 JAR 后安装新 JAR。

实际后台范围与结果见 [验证记录](POWER_VALIDATION.md)。未启动游戏客户端，不把面板服务器构造测试视为画面、键鼠或客户端验证。

## English

Version 0.2.0 supports native ordinary Fusion Mk-I–V and Compact Fusion Mk-I–V, preserving controller IDs and their original GUI. Use **P** to edit per-input-color power budgets. ULV–MAX display units now include LuV, ZPM and UV; the original UEV–MAX numeric IDs remain stable for existing saves.

Only power changes: native candidate traversal, input-buffer order and recipe caching remain unchanged. No order-priority scheduler is added. Continuous idle ingredients can keep the idle recipe selected until existing input automation changes availability. A recipe fully available from unpainted shared inputs uses the unpainted cap, preventing it from borrowing a painted group's higher budget.

Caps constrain overclocking and compact-machine parallelism before ingredient consumption, without underclocking below base recipe EU/t. Startup EU, capacity eligibility, charging, actual tick consumption and hot handoff remain native. Edits apply at the next reaction; saving does not stop or restart a running reaction. The GUI reports the native processing ceiling, not live incoming power. Configuration copying transfers settings only; world saves additionally preserve the in-flight budget for native resume checks.

No client was launched. Refer to the verification record for actual dedicated-server coverage.
