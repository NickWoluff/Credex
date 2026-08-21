# Credex

Credex 是一款仅面向竖屏手机的 Android 服务余额与配额查看工具。它可将已添加服务的账户余额、Token Plan 或 Coding Plan 配额集中展示，并提供 Android 原生桌面小组件。

## 功能

- 支持 OpenAI Codex、DeepSeek、SiliconFlow、Xiaomi MIMO、火山引擎、OpenCode、Kimi、GLM 及自定义接口。
- 支持账户余额、Token Plan、Coding Plan、Agent Plan 和 Codex 时间窗口配额等服务类型。
- 可选择 Material 或 Miuix 界面风格，并提供主题、小组件和通知设置。
- 支持拖拽排序、服务独立配置、内置登录与加密凭据存储。
- 提供 Android 桌面小组件；可选择主服务和副服务，并调整展示样式。

## 项目结构

- `codex-quota-companion/`：Android 应用、服务适配、小组件和设置页面。
- `docs/`：服务接口研究与显示面说明。
- `codex-quota-companion/design/`：设计预览和渲染校验脚本，不会打包进应用。

## 构建与测试

项目使用 JDK 17 和仓库自带的 Gradle Wrapper：

```powershell
.\gradlew.bat :codex-quota-companion:testDebugUnitTest
.\gradlew.bat :codex-quota-companion:assembleDebug
```

Debug APK 输出路径：`build/Credex-app/outputs/apk/debug/Credex-v<版本号>-debug.apk`。

## 隐私与安全

凭据仅保存在本机的 Android Keystore 加密存储中，不会通过展示 Provider 或小组件暴露。部分平台接口和网页登录流程可能随服务商调整而变化；刷新失败时应用会保留上一次成功获取的展示数据。
