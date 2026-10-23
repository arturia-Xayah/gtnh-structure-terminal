# 原生主机动画界面 / Native animated dashboard

0.2.5 将早期全息风格设计落在 Minecraft 原生 GuiContainer 上。没有浏览器运行时、额外服务端 tick 任务或动画网络包。动画由客户端每帧采样单调时钟，库存数据继续沿用现有同步。

## 布局与交互

- 336×284 普通布局；可用尺寸小于 352×300 时用 304×224 紧凑布局，覆盖标准最小 320×240 缩放视口。
- 36 个玩家槽的身份/编号不变。服务端使用普通布局坐标，客户端 initGui 根据布局重新放置同一槽，原生 GuiContainer 处理命中及物品交互。
- 自动吸收/检测继续发送既有 action 0/1。动画按钮只修改客户端配置，服务端拒绝 action 2。
- 配置位于 config/gtnh-modernization-client.cfg，client.storageAnimations，GUI 按钮立即生效并保存；外部修改后重启客户端。
- 悬停容量数据查看未插值的实际值；超过 36 位数字用六位有效数字的指数摘要。库存锁定时可在状态区悬停查看原始原因。

## 动画与绘制

VaultAnimation 不依赖 MC 或 OpenGL。第一次显示直接采用当前值，避免假装从零填满；新数据在 500 ms 内用 smoothstep 过渡，中途更新从当前显示值接续。数值最多分 30 步更新并缓存文本，已用/剩余/百分比保持同一显示数值关系。

比例用 BigDecimal 在数据/过渡步骤变更时计算，不把极大 BigInteger 直接转成 double 相除；2^2000 级容量也不会产生 Infinity/NaN。动画关闭、结构失效或锁定时立即采用当前数值，停止扫描线、旋转光弧与呼吸灯。

VaultMesh 使用可复用数组，最多 8192 顶点，背景、仪表、网格与扫描线合并为一个 GL_TRIANGLES 批次。文字、按钮与原生物品另行绘制。GUI 内不创建世界监听器或后台线程。绘制时局部关闭纹理、深度、剔除、alpha test 并启用混合，通过 push/pop 恢复相关状态与矩阵。环形进度不会依赖继承的面剔除状态。

## 无客户端预览

```sh
python3 tools/render_vault_dashboard.py --java /path/to/java25/bin/java \
  --output /path/to/preview --language zh_CN
```

生成普通/紧凑/极大容量/未成型 PNG 与示例 GIF。工具仅编译生产 VaultLayout/VaultAnimation/VaultMesh/ByteFormat，用 Java2D 消费同一三角形几何，在 headless=true 下执行，不加载 MC。文字与按钮的离线绘制使用替代字体，用于布局核对，**不是游戏截图或 GPU 验收**。图中文字也明确标记此边界。

[普通布局](../assets/vault/dashboard/full.png) · [紧凑布局](../assets/vault/dashboard/compact.png) · [英文](../assets/vault/dashboard/full-en.png) · [极大容量](../assets/vault/dashboard/huge-en.png) · [未成型](../assets/vault/dashboard/invalid.png) · [动画](../assets/vault/dashboard/animation.gif)

## 验证

单元测试检查首次快照、插值中途更新、到达精确终值、int 类型计数边界、大整数比例、关闭/失效停动，以及两种布局的槽位与控制区隔离、几何有限性/顶点预算。后台真实容器检查槽位身份与本地按钮不会修改服务器。发行器逐字节比较 0.2.4 的存储/同步核心，验证没有增加库存写入或同步任务。

未启动游戏客户端。客户端 OpenGL 驱动、实际 Minecraft 字体/资源包、NEI 覆盖层、鼠标拖放及 GUI 缩放仍需客户端验收。后台共享几何预览与容器测试不能替代这些检查。

## English

The dashboard uses a native GuiContainer, two responsive layouts and unchanged player-slot IDs. Client-only 500 ms transitions, a scanning beam, orbit highlights and status breathing add no server packets/tasks. Disabling motion snaps to current values; invalid/locked structures stop effects. The shared production mesh is rendered headlessly for illustrations with substitute fonts, not game screenshots. Unit and dedicated-server checks do not replace OpenGL, resource-pack or client interaction acceptance.
