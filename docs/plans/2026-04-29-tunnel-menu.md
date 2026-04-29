# MC-GO 隧道菜单 Implementation Plan

> **For Hermes:** 按 TDD 执行；先补模型与解析测试，再实现导航、屏幕和启动选择交互。

**Goal:** 为 MC-GO 增加“隧道”一级菜单，支持 FRP/其他隧道的参数添加、配置粘贴导入、列表展示与实时延迟，并把隧道选择接入服务器启动流程。

**Architecture:** 在 App 顶层持有服务器列表与隧道列表状态；新增 TunnelProfile 模型与配置识别逻辑；新增 TunnelScreen 展示列表与添加弹层；ServersScreen 在离线实例启动时弹出“启动配置”对话框，支持选择隧道并处理可自定义端口/固定单隧道配置差异。

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, rememberSaveable, 单元测试（JUnit + Truth）

---

## 任务拆分

1. 新增 `TunnelProfile` / `TunnelKind` / `TunnelSource` / `TunnelConfigFormat` / `TunnelProtocol` 模型与配置识别函数。
2. 为隧道解析、启动端口规则、页面 chrome、样例隧道列表补充 failing tests。
3. 扩展 `McGoPage` / `McGoDestination` / `strings.xml`，插入“隧道”一级导航。
4. 在 `McGoSampleRepository` 增加默认隧道列表样例（手填参数 + 粘贴配置混合）。
5. 新增 `TunnelScreen.kt`：列表、延迟徽标、添加按钮、手填表单、配置粘贴表单。
6. 将服务器列表改为由 App 顶层传入状态；离线实例启动时弹出隧道选择 + 端口配置对话框。
7. 统一设置页按钮/芯片颜色显式跟随 `MaterialTheme.colorScheme.primary`。
8. 更新版本号、运行 `:app:testDebugUnitTest` / `:app:lintDebug` / `:app:assembleDebug`。
9. 独立代码审查通过后提交 git commit，并导出 APK / 直链。
