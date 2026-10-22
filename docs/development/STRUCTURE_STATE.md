# 运行中机器：预览与强制结构重检

核对目标：GTNH 2.9.0-RC-2 清单对应的 GT5-Unofficial **5.09.54.205**。这里的 GT 行为结论来自该版本源码；后台夹具使用合成 StructureLib 多方块验证运行状态，完整整包逐台测试另记录空闲机器扫描前后的 mMachine、进度、EU/t 与朝向快照；两者均不等同于真实 GT 配方运行测试。

当前终端普通右键和 Shift 右键都打开预览，没有绑定原生 `checkStructure(true, ...)`。扫描会重新采集计划和候选材料，但不会主动调用结构元素 `check`、生存搭建或机器朝向 setter。镜像预览通过坐标变换实现，不改变实际机器朝向。关闭界面保存设置也不会开始搭建或原生重检。

原 Alpha 的扫描曾临时调用 `setExtendedFacing` 来切换并恢复镜像，这在 GTNH 中有实际停机风险。0.1.1-alpha 已删除这条预览路径。后台夹具把朝向 setter 设计成改变时停机，验证普通扫描、不同镜像的连续扫描及 Shift 右键不会调用它；同时要求运行进度继续增长。

GT 的原生强制重检具有不同语义：

- `MTEMultiBlockBase.checkStructure` 清空并重新收集仓室、结构错误与线圈记录，再更新 `mMachine`。该函数本身没有直接清零配方进度的语句，但会调用每台机器自己的检查实现，不能把它视为任意机器都无副作用的只读查询。
- 后续 `onPostTick` 发现结构无效且机器允许工作时，会以结构不完整为由调用停机。是否保留进度还取决于原机停机实现。
- `MTEEnhancedMultiBlockBase.setExtendedFacing` 在改变朝向 / 镜像时，对已成型且允许工作的机器主动停机。因此“改一下再改回去”也不能消除影响。
- 终端的“替换并补建”和“拆除”会实际修改方块，可能触发原机结构失效并停机；本次修复针对预览采集，不保证修改中的机器继续生产。

原始源码：

- [结构检查与运行 tick](https://github.com/GTNewHorizons/GT5-Unofficial/blob/5.09.54.205/src/main/java/gregtech/api/metatileentity/implementations/MTEMultiBlockBase.java#L565)
- [改变朝向时的停机路径](https://github.com/GTNewHorizons/GT5-Unofficial/blob/5.09.54.205/src/main/java/gregtech/api/metatileentity/implementations/MTEEnhancedMultiBlockBase.java#L86)

对附属 Mod，仍需核对其自定义 `construct`、`getBlocksToPlace` 和 `checkMachine` 是否自行改变机器状态。后台夹具不能证明所有附属 Mod 都遵守同样行为约定。
