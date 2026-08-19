# OuterView Quota 的四种显示形态

四种形态共享 `OuterView Quota` Companion 的最近成功数据，但分别针对所在屏幕设计；它们不是同一张卡片的缩放版本。

| 形态 | 安装位置 | 设计重点 | 主动刷新 |
| --- | --- | --- | --- |
| Assistant 卡片 | OuterView 的 Assistant 页面 | 避让背屏镜头、系统时间与手势区；快速扫读配额 | 初始化、背屏恢复、每分钟、根层透明触摸目标 |
| Rear Wallpaper | OuterView 的 Wallpaper 页面 | 全画布环境化时钟；配额作为次级信息 | 初始化、背屏恢复、每分钟、根层透明触摸目标 |
| Launcher 小组件 | Android 正面主屏幕 | 48dp 触控、日夜主题、可变宽度、点击进入 App | 手动按钮、系统挂载、App/Job/前台服务刷新后推送 |
| Xiaomi MAML 小组件 | 小米桌面 MAML 宿主 | 显示设置中选定的余额服务，按设置顺序最多三项 | 初始化、恢复、每分钟、根层透明触摸目标 |

## 使用

1. 安装并打开 `codex-quota-companion-debug.apk`，完成 OpenAI OAuth 授权。
2. Assistant：导入 `demo/codex-quota-rear-card/Codex-Quota-Rear-Card.zip`。
3. Wallpaper：导入 `demo/codex-quota-rear-wallpaper/OuterView-Codex-Quota-Wallpaper.mrc`。
4. Xiaomi MAML：构建并导入 `demo/codex-quota-maml-widget/OuterView-Balance-MAML-Widget.zip` 到支持 MAML 的桌面组件宿主。
5. Launcher：在 Companion 的“设置 > 主屏幕”点击“添加配额小组件”；若 Launcher 不支持应用内固定，长按桌面并从小组件列表选择 `OuterView Quota`。

Launcher 小组件的 2×2 紧凑形态只显示一个主窗口，并把“最后更新 HH:mm”放在状态行内；横向扩展后，只有 OpenAI 实际返回 5-hour 窗口时才显示第二窗口。Weekly-only 不保留空占位。中尺寸使用对称 12dp 外边距，紧凑尺寸保持 8dp 外边距和 48dp 刷新触控区。

配额卡在有空间时会把绝对重置时间和相对倒计时合并显示，例如“重置于 07-21 16:44 · 于 6天14小时22分钟后更新”。小组件中尺寸保留完整信息；紧凑尺寸改为单行“重置于 6天14小时后更新”（按空间省略分钟），避免挤压百分比和最后更新时间。

设置页把“持续同步”和“常驻通知”分开。关闭“常驻通知”会立即停止前台服务及其常驻通知，但仍保留可持久化的 JobScheduler 定时刷新；小组件、Assistant 卡片和 Wallpaper 仍可按需刷新。Android 或厂商电池策略可能延迟静默任务，应用会显示最后一次成功更新时间。

App 图标使用 GPT Image 2 生成的彩色配额环作为 adaptive-icon 前景，并提供 Android 13 单色层；刷新、设置、返回等操作图标使用标准 Android/Material 风格矢量资源。

所有形态都只读取或呈现配额、重置时间、方案、余额服务、状态和更新时间。设置页的余额服务卡片提供总开关、四个显示端开关和上移/下移排序；Provider 通过 `/quota/desktop`、`/quota/assistant`、`/quota/wallpaper` 分别返回过滤后的显示数据。OAuth/API 凭证由 Android Keystore 加密，不会提供给 MAML 包或 Launcher 宿主。本方案不在 Android 上运行 Codex，也不需要电脑桥接。
