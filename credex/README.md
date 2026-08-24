# Credex

Credex Android 应用用于集中查看多个 AI 服务的账户余额和配额，并将展示数据提供给 Android 原生桌面小部件。当前版本仅支持竖屏手机；平板适配将在后续单独处理。

## 支持的服务

- OpenAI Codex：5 小时与周配额。
- DeepSeek、SiliconFlow、Xiaomi MIMO、火山引擎、OpenCode、Kimi、GLM：依服务商能力展示账户余额或各类 Plan 配额。
- 自定义接口：使用用户提供的标准余额接口。

服务可独立启用和排序；背屏服务在“设置 > 背屏配置”中分别为 Assistant 与 Wallpaper 选择一个展示服务。Plan 服务可选择展示已使用或剩余配额。

## 小部件与刷新

Android 桌面小部件支持主服务和可选副服务，可在应用的“小部件配置”中调整服务、数值折叠方式、高度与上下偏移。应用通过系统后台任务与用户可选的前台同步服务刷新数据；刷新失败时保留最近一次成功获取的数据。

## 数据安全

登录会话、访问令牌和 API Key 仅保存于 Android Keystore 加密存储。导出的 Assistant/Wallpaper Provider 仅提供展示字段，不包含令牌、账号标识或原始响应。

## 构建

需要 JDK 17：

```powershell
.\gradlew.bat :credex:testDebugUnitTest
.\gradlew.bat :credex:assembleDebug
```

Debug APK 位于仓库根目录的 `build/Credex-app/outputs/apk/debug/Credex-v<版本号>-debug.apk`。

## 兼容性说明

服务商的网页、登录策略和非稳定接口均可能发生变化，届时可能需要重新登录或等待适配更新。Credex 不代表、隶属于或获得任何所列服务商的背书。
