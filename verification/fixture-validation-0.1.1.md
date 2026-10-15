# 0.1.1-alpha 验证记录

日期：2026-10-15。发布 JAR：`dist/structureterminal-0.1.1-alpha.jar`。

**30 项真实后台服务器集成测试全部通过；16 项 JUnit 测试全部通过。** Forge / Minecraft 专用服务器使用 `nogui` 和 `java.awt.headless=true`，只绑定本机随机端口，测试结束自动关闭。本轮没有启动客户端。

## 环境与边界

Minecraft 1.7.10、Forge 10.13.4.1614、StructureLib 1.4.45，开发依赖解析的 GTNHLib 为 0.11.48；目标 GTNH 2.9.0-RC-2 清单的 GTNHLib 为 0.11.52。**这不是完整 GTNH 服务端整合包。** 测试夹具包含一个真实 StructureLib 定义的 3×3×3 多方块，使用铁块、玻璃 / 染色玻璃和以羊毛不同元数据表示的两档测试线圈，经过真实世界 tick、原生 survivalBuild 和正式 BuildService 执行。服务器中的测试玩家有背包及受控连接，但不测试真实客户端发包或登录协议。

没有声称已验证所有 GT 机器、真实线圈发热或实际配方运行。实际 GT5 运行与停机行为另由固定版本源码审查说明，见 `STATE_CHECKS.md`。

## 检查结果

| 用例 | 结果 | 执行后等待 tick |
|---|---|---:|
| empty_survival_build | 通过 | 48 |
| partial_correct_coils_preserved | 通过 | 48 |
| already_complete_consumes_nothing | 通过 | 48 |
| replace_partial_matching_coils_no_double_charge | 通过 | 48 |
| replace_all_coils_to_new_tier | 通过 | 48 |
| replace_mixed_existing_coil_tiers | 通过 | 48 |
| replacement_missing_new_material_preserves_old | 通过 | 48 |
| replacement_requires_confirmation | 通过 | 48 |
| replacement_place_cancel_rolls_back | 通过 | 48 |
| replacement_keeps_filled_container | 通过 | 48 |
| replace_glass_across_block_ids | 通过 | 48 |
| build_with_independent_glass_channel | 通过 | 48 |
| replacement_break_event_cancellation_respected | 通过 | 48 |
| replacement_foreign_obstruction_is_preserved | 通过 | 48 |
| wrong_coil_is_not_silently_destroyed | 通过 | 48 |
| missing_materials_stop_without_free_blocks | 通过 | 48 |
| resume_after_materials_restocked | 通过 | 48 |
| foreign_obstruction_preserved | 通过 | 48 |
| demolition_returns_all_ordinary_materials | 通过 | 48 |
| demolition_requires_confirmation | 通过 | 48 |
| filled_chest_is_never_demolished | 通过 | 48 |
| empty_tile_entity_also_skipped_by_default | 通过 | 48 |
| break_event_cancellation_respected | 通过 | 48 |
| full_inventory_demolition_drops_are_conserved | 通过 | 48 |
| invalid_selected_material_rejected | 通过 | 48 |
| readonly_scan_keeps_running_state | 通过 | 48 |
| mirrored_preview_does_not_call_orientation_setter | 通过 | 48 |
| shift_right_click_has_no_native_force_check | 通过 | 48 |
| logout_cancels_job | 通过 | 48 |
| changing_held_tool_stops_job | 通过 | 48 |

普通只读扫描、镜像预览和 Shift 右键三个用例均记录：`running=true`、`progress_ticks=48`、`orientation_setter_calls=0`、`structure_check_calls=0`。扫描未修改背包。完整拆除用例回收 9 个铁块、8 个玻璃和 8 个测试线圈，控制器保留；满背包用例按背包、世界方块与掉落物合计检查材料数量。

详细结果见 `verification/server-tests.json`，真实服务端日志见 `verification/server-tests.log`。测试区域使用 Forge 区块票据保持加载，并把测试玩家登记到服务器与世界两处玩家列表，避免区块卸载或无人世界在 1200 tick 后暂停更新导致无效测试。进度断言没有被跳过。

## 本轮修复

- 预览改为坐标变换，移除临时切换实际镜像导致 GT 多方块停机的路径。
- StructureLib 默认在服务端跳过 hints 遍历；新增受终端线程采集状态限制的只读遍历，绕开提示、检查和放置回调。普通 StructureLib 调用保持原行为。
- 新增需确认的普通结构方块替换；先检查新材料，再原生放置，成功才扣料并回收旧材料，失败恢复当前原方块。
- 回收不调用可能间接引用客户端方法的 getPickBlock，使用服务端物品映射；玻璃按完整方块返回。
- 容器默认跳过，取消的破坏 / 放置事件受到尊重，缺料及阻塞不会无限执行。

## 自动化与发布检查

JUnit 16 项覆盖状态机、随机进展序列、边界、设置 NBT、材料标识及 ASM 转换分支。新增 ASM 用例验证只在终端采集 hints 且没有原生检查请求时启用只读遍历。

Checkstyle、Spotless、完整 Gradle build 通过。发布与开发 JAR 的 43 个类全部为 Java 8 major 52，发布 JAR 不包含 serverTest / QA 夹具，核心插件清单正确。SHA-256 见 `dist/SHA256SUMS`。Python 辅助脚本通过 AST 解析，Bash 入口通过语法检查。

未验收 GUI 布局、复杂模型、真实 GT5 机器配方、AE 网络取料及完整 GTNH 整包逐机兼容。工程压缩包不包含游戏文件、存档、JDK 或 Gradle 缓存。
