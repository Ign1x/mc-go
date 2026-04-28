# MC-GO 前端设计提示词

## 项目定位
**MC-GO** 是一个安卓端 Minecraft Java 版手机开服应用。

目标方向：
- 用手机开 Java 版 MC 服务器
- 风格偏现代、专业、轻电竞感
- 保留少量 Minecraft 元素，但整体保持高级、简洁、可商用的 UI 气质

---

## 全局视觉补充词（推荐追加到每条提示词末尾）
```text
light translucent background, richer but controlled colors, compact typography, smaller font sizes, Material You meets professional dashboard UI, subtle voxel-inspired Minecraft accents, premium mobile app concept, polished spacing, soft shadows, rounded corners, Figma shot, highly usable real product UI
```

中文意图：
- 偏浅色 / 半透明背景
- 色彩更丰富，但不过度花哨
- 字体更小一些，信息密度更高
- 有 Minecraft 像素 / 方块暗示，但不要幼稚化
- 整体更像可落地的高质量产品设计

---

## 1. 整体视觉基调与底部导航栏
### 英文提示词
```text
Mobile frontend UI design for a Minecraft server hosting application named "MC-GO". Clean, elegant, and modern gamer aesthetic. The interface shows a standard bottom navigation bar with 3 items (icon + text, WeChat style): "Status" (performance dashboard icon), "Servers" (Minecraft block or server rack icon), and "Settings" (gear icon). Subtle Minecraft visual cues but maintaining a highly professional and minimalist look. Soft shadows, rounded corners. UI/UX masterpiece, Figma design. --ar 3:4 --v 6.0
```

### 中文释义
一个名为“MC-GO”的 Minecraft 服务器托管应用（开服软件）的移动端前端 UI 设计。干净、优雅且现代的玩家美学。界面展示一个标准的底部导航栏，包含 3 个选项（图文并排，类似微信）：
- **状态**（性能仪表盘图标）
- **服务器**（Minecraft 方块或机架图标）
- **设置**（齿轮图标）

带有微妙的 Minecraft 视觉元素，但保持高度专业和极简的外观。柔和阴影、圆角、Figma 风格高质量设计。

---

## 2. 状态页（服务器手机性能监控）
### 英文提示词
```text
Mobile frontend UI design, system status dashboard for a Minecraft mobile server hosting app. Elegant layout. The screen displays smooth dynamic line charts (spline charts) monitoring the phone's live performance as a server: CPU usage, RAM memory, Network I/O, and Battery current. Glassmorphism cards, glowing gradients on the line charts (tech blue and vibrant green). Showing a small "Server Running" status badge. High-end UX/UI, clean white background. --ar 9:16 --v 6.0
```

### 中文释义
移动端前端 UI 设计，Minecraft 手机开服应用的系统状态仪表盘。屏幕显示平滑的动态折线图，用于监控手机作为服务器的实时性能：
- CPU 使用率
- RAM 内存
- 网络 I/O
- 电池电流

可使用毛玻璃卡片、科技蓝与活力绿的发光渐变折线图，并展示一个小巧的 **Server Running** 状态徽章。整体为干净浅色背景。

### 细化建议
- 顶部显示当前服务器名称与运行时长
- 中部放 2x2 性能卡片 / 图表
- 底部可以放最近日志摘要或异常提示入口

---

## 3. 服务器页（MC 实例管理）
### 英文提示词
```text
Mobile frontend UI design, server management screen for a Minecraft hosting app. Minimalist and elegant. Features a list of Minecraft server instances (cards). Each card shows the server name, server icon (Minecraft style), online player count (e.g., 5/20), and a status indicator (green dot for online). A prominent floating action button (FAB) for "Create New Server". Clean typography, soft drop shadows. Material You vibe. --ar 9:16 --v 6.0
```

### 中文释义
移动端前端 UI 设计，Minecraft 开服应用的服务器管理界面。极简且优雅。包含一个 Minecraft 服务器实例卡片列表。每个卡片显示：
- 服务器名称
- 服务器图标（Minecraft 风格）
- 在线玩家人数（例如 `5/20`）
- 状态指示器（绿点表示在线）

并配一个醒目的悬浮操作按钮（FAB），用于 **Create New Server**。风格参考 Material You，排版干净，阴影柔和。

### 细化建议
- 卡片右侧可放快捷操作：启动 / 停止 / 编辑
- 支持展示 Java 版本、端口、地图名等副信息
- 离线服务器可采用灰色状态与弱化背景

---

## 4. 设置页（应用偏好与工具配置）
### 英文提示词
```text
Mobile frontend UI design, settings screen for a Minecraft server hosting app. Elegant, clean, and categorized interface. The layout consists of clickable rounded cards. Categories include: Appearance, Notifications, Downloads & Storage, Diagnostics, and Experimental Features. Each card has a sleek icon, title, and a right-pointing arrow indicating tappable sub-menus. Large rounded corners, light gray background with white cards. Premium UI/UX design. --ar 9:16 --v 6.0
```

### 中文释义
移动端前端 UI 设计，Minecraft 手机开服应用的设置界面。优雅、干净且分类明确。布局由可点击的圆角卡片组成。类别更偏应用与工具层，而不是开服参数，例如：
- Appearance（界面与外观）
- Notifications（通知与提醒）
- Downloads & Storage（下载与存储）
- Diagnostics（日志与诊断）
- Experimental Features（实验性功能）

每个卡片配有精致图标、标题，以及指向右侧的箭头，表示可点击进入子菜单。整体使用浅灰背景与白色卡片。

### 细化建议
- 首屏更聚焦主题、提醒、缓存与日志等常用偏好
- 高级项折叠，避免首屏信息过载
- 可加入导出日志、缓存清理、实验功能开关等入口

---

## 推荐的统一设计语言
如果你后续要把这些提示词转成真正的产品设计稿，建议统一采用以下语言：
- **配色**：浅色基底 + 蓝绿科技感强调色
- **质感**：卡片化、柔和阴影、少量玻璃拟态
- **图标**：简洁线性图标，少量 Minecraft 方块/像素元素点缀
- **字体**：小一号、提升信息密度，避免“游戏海报化”
- **布局**：更像专业工具类 App，而不是纯游戏陪玩界面

## 下一步建议
1. 先把这 4 组提示词用于 AI 出图 / Figma 参考图
2. 确定最终风格（更偏 Material You / 更偏玻璃拟态 / 更偏专业仪表盘）
3. 基于定稿继续拆成组件：
   - 底部导航
   - 性能图表卡片
   - 服务器实例卡片
   - 设置分类卡片
4. 然后再进入安卓实际前端实现
