> 历史开发构建记录，保留当时的规则、哈希与验证范围；当前发行版见 [最新验证](VALIDATION.md)。

# 0.3.0 · 可配置读取缓存验证

2026-11-02。目标 GTNH 2.9.0、Minecraft 1.7.10；运行 Java 25.0.4.1，生产 class 保持 Java 8 字节码。所有运行验证均为隔离后台服务器，未启动客户端。

本轮是本地 **cache-configured** 开发变体，Mod 内部版本仍为 0.3.0-alpha。此前 [读取优化](VALIDATION_0.3.0_READ.md)、[BigInteger 初次整合](VALIDATION_0.3.0_QUANTITY.md) 和 [读取性能计时](VAULT_READ_OPTIMIZATION.md) 保留历史范围，不将旧性能数字当成本变体的新基准。

## 结果

| 范围 | 结果 |
|---|---|
| 单元测试 | **111 项通过** |
| 小预算、负数配置回退、文件修改不热加载 | **7 项通过** |
| 独立进程重启，全部缓存关闭 | **6 项通过** |
| 超大数量与双聚合器 AE / 重启 | **6 / 2 项通过** |
| 优先级、Reshuffle、输出 / 重启 | **14 / 2 项通过** |
| 原有仓库存取 / 重启 | **38 / 4 项通过** |
| 生产包 | **159 个 Java 8 class，与后台夹具逐字节一致** |

共 **79 项后台检查**，包括四组独立进程重启。生产代码变化限定在仓储模块，其他模块字节码与前一读取优化变体一致；没有把以前的功率、下单及逐机检查重新计为本轮全部重跑。

新增九个单元测试覆盖 LRU 访问顺序、局部/全局字节限额、单条拒绝、扫描不驱逐热项、0 关闭、long 预算边界、随机记账、跨缓存并发与清理，以及无数组复制的资源长度计算。

真实后台使用两台成型仓库连接同一个供电 AE 网络，持有 255 位以上数量、19 种物品/流体类型，其中包括约 400 KB 的 NBT。配置将键/原型限制为每通道 2 条，并设置很小的字节预算，仍须完整枚举全部类型、执行原生模拟与实际存取且数量守恒。另验证输出缓存的配置及全局记账。第一进程保存每台仓库 UUID 和逐类型完整数量，改配置后停止；第二进程以全部预算为 0 启动，核对原库存、可见 long 饱和量、正常 AE 存取与缓存保留量为 0。

用户指南说明预算是计费权重，并非 JVM 堆硬上限。账本、读取视图及 AE 返回列表等不计入这些键/原型预算。预算不足时完整解码，不省略类型。配置仅在 Mod 初始化时载入，不提供运行时重载。

## 数据与安装包

- [验证清单、生产与夹具 SHA-256](../../verification/vault-cache-config/manifest.json)
- [配置首进程](../../verification/vault-cache-config/backend/cache_config/cache-config-tests.json)
- [关闭缓存后的新进程](../../verification/vault-cache-config/backend/cache_config/cache-config-tests-restart.json)
- [后台运行汇总](../../verification/vault-cache-config/backend/summary.json)
- [默认配置与修改方法](../features/CACHE_CONFIGURATION.md)
- 历史本地构建 `cache-configured`（未作为发行附件上传）

JAR 不包含测试入口、基准类、冻结基线或探针；原文件和旧构建保留，不覆盖历史实测证据。尚未推送或发布到 GitHub。未重跑性能矩阵，也未启动客户端作 GUI、字体或 OpenGL 验收。原生 AE 仍使用 long 可见窗口；新库存继续使用 AVS4，没有改变存档格式或库存容量规则。

## 复现

`-PmodernizationPackTests=true` 构建显式测试夹具，再使用 `tools/run_modernization_backend.py --suites cache_config big_quantity vault_io vault`，其余参数见 [构建说明](BUILDING.md)。配置套件初始文件由运行器创建为小预算，第二进程读取第一进程写入的全 0 文件；不修改源整合包目录。使用 `tools/verify_vault_cache_config.py` 将最终生产包与执行夹具、111 项单元报告及 79 项后台检查绑定。

## English

The local cache-configured 0.3.0 variant passes 111 unit tests and 79 dedicated-server checks, including four fresh-process restarts. Tiny entry/byte limits and a subsequent all-zero configuration preserve complete native AE enumeration, exact stock and transfers. Negative input is normalized to defaults; file changes require a full process restart. Key/prototype/export caches share a weighted global budget; accounted bytes are not a heap cap. The 159 production Java-8 classes match the executed fixture byte-for-byte. Other feature bytecode is unchanged. No new performance matrix or client visual acceptance is claimed; no GitHub publication has occurred.
