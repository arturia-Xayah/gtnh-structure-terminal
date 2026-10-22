# 0.1.5-alpha 开发版：样板原料可合成标记

2026-10-21，目标 GTNH 2.9.0-RC-2 / AE2 rv3-beta-1080-GTNH，使用 Java 25.0.4.1 构建并运行隔离后台服务器。未启动客户端。

- `build` 通过，33 项 JUnit 检查通过（原有 29 项，加 4 项客户端挂钩的字节码兼容检查）。
- 整包后台 **51/51** 用例通过：新增 **18** 项原生 AE 混合库存查询检查，以及原有 **33** 项下单、组件控制、路线校验与批量派发回归。
- 正式运行 JAR 中的 **81 个类** 与测试夹具中的对应类逐字节一致；JAR 不包含后台夹具，所有类均为 Java 8 字节码，客户端字体调用已重映射。
- [验证摘要](verification.json)、[后台报告](ae-report.json)、[JUnit XML](unit-tests) 随源码保留。

新增场景包括：空网络、只有库存、有库存也有样板、零库存可合成、不修改输入、断电、空输入、缺少仓库、物品 NBT、物品元数据、不同物品、流体零库存、有流体库存、不同流体、原生流体身份规则、样板移除、清空网络、切换网络。

最初测试错误地假设此 AE 版本区分自定义流体 NBT；真实后台发现 `AEFluidStack.create` 会丢弃该标签。最终代码继续使用原生 `findPrecise`，测试与文档已修正；没有更改 AE 的流体匹配规则。

客户端只注册四个显示相关 Mixin；后台服务器仅注册原有公共 Mixin。字节码检查核对输入槽工厂的第一处创建确为 `inputsSync`、第二处为 `outputsSync`，以及访问器字段、扩展终端继承和 tooltip 签名。**这些检查没有执行客户端 Mixin 或 OpenGL，不能视为真实客户端注入或画面验证。**

重现：按 [开发文档](../../docs/development/BUILDING.md) 构建 AE 后台夹具，在全新服务器目录运行，随后不带测试标志重新构建生产 JAR，再执行：

```sh
python3 tools/verify_craftable_inputs.py \
  --jar dist/GTNH-2.9.0-RC-2/structureterminal-0.1.5-alpha-GTNH-2.9.0-RC-2.jar \
  --fixture /path/to/test-fixture.jar \
  --report /path/to/test-server/ae-order-report.json \
  --unit-results build/test-results/test \
  --output /path/to/verification.json
```

## English

Development validation for GTNH 2.9.0-RC-2: 33 unit/bytecode checks and 51 full-pack dedicated-server cases passed, including 18 new native AE inventory lookup cases and all 33 previous optimizer regression cases. All 81 production classes match the tested fixture byte for byte, target Java 8, and exclude test entry points. The initial fluid-NBT test assumption was corrected to follow native AE normalization. No client was launched; client Mixin execution and visual rendering remain untested.
