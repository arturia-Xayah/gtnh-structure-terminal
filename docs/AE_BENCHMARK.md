# 星门下单计算实测 / Stargate planning comparison

以下数据来自合并前的独立 AE Order Optimizer 0.1.0-alpha；合并版沿用其计算与派发代码，本页不是一次新的合并版星门运行。

These measurements were taken with the standalone optimizer before merging. The merged module retains its planning and dispatch code; this is not a new Stargate run of the merged JAR.

GTNH **2.9.0-RC-2**，AE2 `rv3-beta-1080-GTNH`、AE2FC `1.5.113-gtnh`、Java25，6 GiB 堆。真实专用服务器，同一进程交替测试普通、Lite、Mod，未启动客户端。每种场景、每种模式共四轮，第一轮预热，表格取其余三轮中位数。Mod 详细报告关闭。没有修改交付 JAR。

目标均为一个本地星门：**底座1、普通环块8、符文环块7、拨号控制器1**。用一个仅供测试的汇总样板合并目标；运行燃料、对端星门、可选升级不计入。只测试下单计算，没有提交完整星门生产任务。

## 原来的完整配方场景

沿用此前实际加载配方生成的 2,398 个样板和 426 类供应物。路径含循环与多输出，普通和 Lite 没有形成已证明正确、可提交的完整订单。

| 计算器 | 累计调用耗时中位数（范围） | 等待计划中位数 | 结果 |
|---|---:|---:|---|
| 原版普通 | 3964.377 ms（3833.745–4072.000） | 32754.361 ms | gui.appliedenergistics2.CraftingStepLimitExceeded |
| 原版 Lite | 27.093 ms（20.005–142.048） | 27.098 ms | 返回缺料计划：15 类 |
| 新 Mod（详细关闭） | 4.576 ms（4.292–7.128） | 4.576 ms | AEO_MULTI_OUTPUT |

**Mod 的迅速报错不是成功秒下星门，不能拿来算下单加速倍数。** 这是上一版验证报告缺失的关键对比。

首个错误点为氢的多输出候选。当前实现先展开选定样板图、再扣除库存，所以检查范围可能包含已有充足库存材料的生产候选；本轮星门测试暴露了这个边界。库存感知的路径剪枝仍是待改进项，不能声称已只验证最终实际投产的全部配方。

## 单输出、无环的补充场景

从同一份完整配方导出表重新选择依赖路径，保留每份原配方输入量及第一产物数量，移除 115 个副产物槽，共 **1,695 个样板**（含汇总样板）。图按类型/NBT 去重，再独立汇总共享依赖，计算整数批次和原料清单；与后台计划逐项核对。

为切断反馈及补足本导出中未找到合格主产物配方的材料，显式新增了 **51 类供应边界**，共 448 类供应物，原有基础供应规则中使用 397 类。例如普朗克电路、部分奇点、高级流体等可在新增边界中；完整清单见 [新增供应 CSV](../verification/ae-optimizer/stargate/extra-supply-boundary.csv)。**本组是简化供应边界下的星门路径，不能称为原始供应边界下从基础原料成功下单完整星门。**

| 计算器 | 累计调用耗时中位数（范围） | 等待计划中位数 | 结果 |
|---|---:|---:|---|
| 原版普通 | 3732.558 ms（3375.459–3955.502） | 30664.140 ms | gui.appliedenergistics2.CraftingStepLimitExceeded |
| 原版 Lite | 9.357 ms（9.242–10.326） | 9.357 ms | 计划返回，无缺料；原料清单与独立计算一致 |
| 新 Mod（详细关闭） | 10.003 ms（6.141–10.906） | 10.004 ms | 计划返回，无缺料；原料清单与独立计算一致 |

有效 DAG 场景中，新 Mod 的累计计算耗时中位数约为原版 Lite 的 **1.07 倍**；本次未获得对 Lite 的加速。 单进程三次测量用于本机比较，两种快速模式的耗时范围重叠，不能证明稳定提速；不是跨机器性能保证；普通计算器若触及步骤上限，也不计算成功订单加速比。


## 数据与复现范围 / Data and scope

只测服务器端计算，不包含终端客户端网络往返、GUI 绘制或真实星门产线生产。累计调用计时覆盖 `beginCraftingJob` 和 `simulateFor`；等待时间截至结果首次可用。普通计算器保持默认每 tick 5 ms 和 2,000,000 步上限。

- [24次计时 CSV](../verification/ae-optimizer/stargate/measurements.csv)
- [分组统计与物料清单检查](../verification/ae-optimizer/stargate/comparison.json)
- [原始独立版报告（gzip JSON）](../verification/ae-optimizer/stargate/standalone-report.json.gz)
- [新增供应边界](../verification/ae-optimizer/stargate/extra-supply-boundary.csv)
- [合并版后台回归](../verification/merged-0.1.4/README.md)

The timings cover server-side planning only, excluding client networking, rendering and real production. The simplified DAG adds 51 supply types, so it is not a verified complete Stargate plan from the original raw-material boundary. Fast rejection is not a successful order speedup. New merged-build regression tests are reported separately.
