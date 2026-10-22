# LuV 仓库外观与配方 / LuV vault update

版本：0.2.1-alpha，目标 GTNH 2.9.0-RC-2。

## 结构与兼容

结构材料由独立 `aestoragevault:casing` 改为 GT 原生 `ItemList.Casing_LuV`，即 `GregTechAPI.sBlockCasings1` 的 metadata 6。仍为 23 个外壳、1 个主机、1 个标准输入总线、1 个 ME 接口，中心留空。旧自定义外壳继续注册以保留旧存档方块，但不能用于新结构，也不再提供旧低等级合成配方。

旧主机、接口、库存 UUID、NBT 最大容量、外部数据目录与文件格式不变。升级后旧结构需更换 23 个外壳才能恢复成型和 AE 访问。输入总线允许原有各等级，不会通过外观升级为 LuV 实际机器。

## 制作门槛

两条配方均在 `RecipeMaps.assemblerRecipes` 注册，使用 `TierEU.RECIPE_LuV = 30,720 EU/t`：

| 输出 | 输入 | 流体 | 时间 |
|---|---|---|---|
| 主机 ×1 | LuV Hull ×1、LuV 电路 ×4、LuV 传感器 ×2、ME 驱动器 ×1 | 焊锡合金 576 L | 600 tick |
| ME 接口 ×1 | LuV Hull ×1、LuV 电路 ×2、LuV 发射器 ×2、ME 驱动器 ×1 | 焊锡合金 288 L | 400 tick |

电路使用矿词匹配。没有工作台后备配方，避免仅替换材料却仍可在低等级机器制造。

## 材质

主机和接口均使用原生 LuV 外壳作为底层，通过独立方块渲染器叠加 64×64 RGBA 原创面板。主机为存储立方体终端，ME 接口为类似输入总线的凹入式物理插口，带金属边框、底部触点和输入箭头。统一机身保留机器的功能图案。

GTO 存储器像素面板及原生 LuV 机身仅作为设计参考。使用内置 image_gen 生成两份原创透明面板，再以 Java ImageIO 最近邻转换为运行时尺寸；接口初版屏幕已按反馈改为物理插口；修订提示词见 [access-socket-revision.json](../assets/vault/access-socket-revision.json)。原始母图与完整提示词见 [素材目录](../assets/vault/) 和 [generation.json](../assets/vault/generation.json)。原生 GT 外壳贴图由整合包提供，未复制进 Mod。

`VaultStructure.inspect` 提供服务端存储和材质判定共用的只读结构检查，不加载额外区块。`VaultHatchSkinMixin` 只对属于有效、已成型仓库的标准输入总线替换返回的基础 `ITexture`，前面仍调用 GT 原有 active/inactive overlay。

不调用 `updateTexture`，不写入染色、等级、朝向、库存或总线材质 NBT。结构失效或总线不属于仓库时，直接返回 GT 原显示路径，因此旁边的其他总线不会被改色。客户端收到主机形成状态后重新构建附近材质显示；网页预览可以对比成型/未成型，但不等于客户端 OpenGL 验收。

## English

Version 0.2.1 requires native LuV casings (GT casing block metadata 6) and LuV assembler recipes at 30,720 EU/t. Legacy block registrations and vault data remain; old structures need their 23 casings replaced. Original 64×64 transparent blue/white overlays sit on the native LuV body. A read-only GT texture hook supplies the same body to formed member buses while preserving native port/color indicators; it never rewrites hatch tier, dye or saved texture fields. Non-member and broken structures retain native rendering. Masters, exact prompts and generation method are retained alongside the assets. The game client has not been launched.
