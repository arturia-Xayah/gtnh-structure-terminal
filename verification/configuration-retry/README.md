# GTNH 2.9.0-RC-2 配置复测

实际尝试 **30 个 GT 注册表多方块控制器条目**。同一机器的不同注册 ID 分别记录。

完整整包、真实方块和生存库存操作；默认尺寸 / 模式，按 retry-plan.json 指定能源仓等级，通过显式选择仓室自动启用原生仓室规则；非 GT TileEntity 已纳入材料账。此复测不覆盖基线。每阶段有 240 tick 时间预算。

这不涵盖每台机器的所有尺寸、仓室等级、附属模块或运行配方；非 GT 注册表控制器不在本轮清单内。

原生搭建器报告完成、实际机器成型、材料账平衡分别统计；没有把缺料、超时或环境限制算作通过。

| 分类 | 数量 |
|---|---:|
| BUILD_BLOCKED | 1 |
| BUILD_TIME_LIMIT | 1 |
| BUILT_NOT_FORMED | 2 |
| DEMOLITION_REMAINDER | 1 |
| MATERIAL_ACCOUNTING_REVIEW | 1 |
| OPERATION_CHECKS_PASS | 20 |
| POST_OPERATION_NOT_FORMED | 3 |
| REPLACE_BLOCKED | 1 |

`OPERATION_CHECKS_PASS` 只表示本轮配置的结构操作检查通过，不表示机器已经通电运行配方或 GUI 已验收。
`BUILT_NOT_FORMED` 需要核对仓室电压等级、催化物、控制器物品或机器专用配置。`MATERIAL_ACCOUNTING_REVIEW` 保留逐物品差额，其中可能包含流体流动 / 元数据转换，不能直接解释为复制或丢失漏洞。

逐台明细见 `machines.csv`，原始阶段结果见 `report.json`。

| ID | 机器 | 分类 |
|---:|---|---|
| 141 | 流体钻机 II | OPERATION_CHECKS_PASS |
| 142 | 流体钻机 III | OPERATION_CHECKS_PASS |
| 143 | 混凝土回填机 | OPERATION_CHECKS_PASS |
| 144 | 进阶混凝土回填机 | OPERATION_CHECKS_PASS |
| 148 | 无尽流体钻机 | OPERATION_CHECKS_PASS |
| 149 | 流体钻机 IV | OPERATION_CHECKS_PASS |
| 1000 | 工业高炉 | OPERATION_CHECKS_PASS |
| 1157 | 流体钻机 | OPERATION_CHECKS_PASS |
| 1158 | 采矿场 | OPERATION_CHECKS_PASS |
| 1177 | 采矿场 II | OPERATION_CHECKS_PASS |
| 1178 | 采矿场 III | OPERATION_CHECKS_PASS |
| 1179 | 采矿场 IV | OPERATION_CHECKS_PASS |
| 12698 | 风车 | DEMOLITION_REMAINDER |
| 12712 | 细菌培养缸 | BUILD_TIME_LIMIT |
| 12729 | 深地热泵 | OPERATION_CHECKS_PASS |
| 12734 | 电动聚爆压缩机 | MATERIAL_ACCOUNTING_REVIEW |
| 12739 | 虚空采矿场 III | OPERATION_CHECKS_PASS |
| 12740 | 虚空采矿场 II | OPERATION_CHECKS_PASS |
| 12741 | 虚空采矿场 I | OPERATION_CHECKS_PASS |
| 12792 | 极限工业温室 | POST_OPERATION_NOT_FORMED |
| 13001 | Research Completer | OPERATION_CHECKS_PASS |
| 14002 | Planetary Gas Siphon | POST_OPERATION_NOT_FORMED |
| 14202 | Industrial Apicultural Acclimatiser and Drone Domestication Station | BUILD_BLOCKED |
| 15541 | 原木拟生场 | REPLACE_BLOCKED |
| 15548 | 工业电弧炉 | BUILT_NOT_FORMED |
| 15555 | 工业锻造锤 | OPERATION_CHECKS_PASS |
| 15559 | Planetary Gas Siphon | POST_OPERATION_NOT_FORMED |
| 15567 | 无尽流体钻机 | OPERATION_CHECKS_PASS |
| 15750 | 熵变处理厂 | OPERATION_CHECKS_PASS |
| 32024 | Large Essentia Smeltery | BUILT_NOT_FORMED |
