# mc-go

> **MC-GO**：安卓端 Minecraft Java 版手机开服工具

## 项目目标
- 在安卓手机上启动和管理 **Minecraft Java 版服务器**。
- 前端优先，先完成移动端 UI/UX 设计与交互结构。
- 后端计划使用 **Rust** 实现核心管理能力。

## 当前阶段
当前已完成 **前端首版**，包含 3 个底部导航主页面：
1. **Status / 状态**：手机作为服务器时的性能状态监控
2. **Servers / 服务器**：Minecraft 实例管理
3. **Settings / 设置**：应用偏好与工具配置

## 当前前端技术方案
- **Android UI**：Kotlin + Jetpack Compose + Material 3
- **最低系统支持**：Android 8.0（API 26）
- **编译 / 目标 API**：Android 16 / API 36
- **构建 JDK**：Java 21（Kotlin / Java 字节码目标 17，兼顾新工具链与稳定兼容）
- **当前版本**：`versionCode 86` / `versionName 0.2.76`

## 已初始化文件
- `docs/frontend-ui-prompts.md`：MC-GO 前端设计提示词整理版
- `app/`：安卓前端工程（可直接构建 APK）

## 已完成内容
- MC-GO 三页式底部导航前端框架
- 状态页：运行状态卡片、性能指标卡片、趋势图、事件摘要
- 服务器页：实例卡片列表、在线/离线状态、快捷操作入口
- 设置页：分类设置入口卡片
- 单元测试与 Debug APK 构建流程

## 建议的下一步
- 把“创建服务器 / 控制台 / 编辑”接到真实数据流
- 增加实例详情页、日志流页、开服向导页
- 再衔接 Rust 后端能力与前端数据结构
