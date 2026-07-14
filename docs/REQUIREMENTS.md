# SleepWatch 需求分析与架构文档

> 归档日期：2026-07-14

---

## 1. 项目概览

**目标**：一款轻量级 Android 应用，在用户设定的就寝时间后自动监测手机使用情况，通过全屏弹窗提醒用户放下手机入睡，记录睡眠数据，并提供统计分析与成就激励。

**目标设备**：红米 K90 Pro Max / HyperOS 2.x (基于 Android 15, API 35)
**最低兼容**：Android 8.0 (API 26)，覆盖绝大多数在用设备

---

## 2. 技术栈

| 层级 | 技术选型 | 版本 | 选择理由 |
|------|----------|------|----------|
| 语言 | Kotlin | 2.0+ | 原生性能，系统 API 直接调用，低开销 |
| UI 框架 | Jetpack Compose + Material 3 | BOM 2024.12+ | 声明式 UI，开发效率高，自动适配深色模式 |
| 架构模式 | MVVM + Clean Architecture | - | 关注点分离，可测试性好 |
| 依赖注入 | Hilt | 2.51+ | Google 官方推荐，Compose 集成好 |
| 数据库 | Room | 2.6+ | 类型安全的 SQLite 抽象，支持 Flow 响应式查询 |
| 后台调度 | AlarmManager + Foreground Service | - | 精确定时唤醒，Doze 模式下仍可执行 |
| 图表 | Vico | 2.0+ | Compose 原生图表库，轻量，动画流畅 |
| 构建工具 | Gradle (Kotlin DSL) + AGP | 8.5+ | 官方推荐构建系统 |
| Min SDK | 26 (Android 8.0) | - | 覆盖 95%+ 设备 |
| Target SDK | 35 (Android 15) | - | 匹配目标设备 |

---

## 3. 架构设计

### 3.1 分层架构

```
┌─────────────────────────────────────────────┐
│              Presentation Layer              │
│   Compose UI + ViewModels + Navigation       │
├─────────────────────────────────────────────┤
│               Domain Layer                   │
│   Use Cases + Models + Repository Interfaces │
├─────────────────────────────────────────────┤
│                Data Layer                    │
│   Room DB + Repository Impls + DataStore     │
├─────────────────────────────────────────────┤
│             Service Layer                    │
│  ForegroundService + Receivers + AlarmManager│
└─────────────────────────────────────────────┘
```

### 3.2 模块划分

```
app/
├── di/                    # Hilt 模块
├── data/
│   ├── db/                # Room 数据库、DAO、Entity
│   ├── repository/        # Repository 实现
│   └── datastore/         # DataStore 偏好设置
├── domain/
│   ├── model/             # 领域模型
│   ├── usecase/           # 业务用例
│   └── repository/        # Repository 接口
├── service/
│   ├── MonitorService.kt  # 前台监测服务
│   ├── AlarmScheduler.kt  # AlarmManager 调度器
│   └── receiver/          # BroadcastReceiver
├── ui/
│   ├── home/              # 首页
│   ├── settings/          # 设置页
│   ├── statistics/        # 统计页
│   ├── achievements/      # 成就页
│   ├── setup/             # 权限引导页
│   └── alert/             # 全屏弹窗 Activity
└── util/                  # 工具类
```

### 3.3 导航结构

- **底部导航栏 (NavigationBar)**：首页 / 统计 / 成就 / 设置（4 个 Tab）
- **Setup 页面**：首次启动时作为引导流程展示，后续仅在权限缺失时从设置页触发
- **Alert 页面**：全屏弹窗 Activity，从任意页面弹出，关闭后回到原页面
- **NavGraph**：使用 Navigation Compose 管理，底部导航的 4 个页面为顶级目的地，Setup 和 Alert 为独立路由

---

## 4. 核心功能点

### 4.1 屏幕状态监测

- **检测方式**：`BroadcastReceiver` 监听 `ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF`，结合 `PowerManager.isInteractive` 双重校验
- **前台服务**：`MonitorService` 作为 `foregroundServiceType="specialUse"` 运行，保持常驻通知栏（低优先级）
- **调度策略**：`AlarmManager.setExactAndAllowWhileIdle()` 精确唤醒检查
- **状态机**：
  ```
  IDLE → MONITORING（到达检测开始时间）
       → ALERTING（检测到亮屏，触发提醒）
       → SLEEP_DETECTED（连续N次息屏，认定入睡）
       → IDLE（次日重置）
  ```
- **边界情况处理**：
  - **跨午夜监测**：以"监测开始时间"为当日分界线（如监测开始时间为 23:00，则 23:00 至次日 23:00 为同一个监测周期）
  - **服务被系统杀死**：`START_STICKY` 自动重启 + AlarmManager 作为兜底调度，恢复 MONITORING 状态
  - **AlarmManager 未触发**：下一次 `SCREEN_ON/OFF` 广播事件自动恢复状态机，不丢失监测
  - **用户手动关闭服务**：清除当晚所有状态，转入 IDLE，不生成半成品记录
  - **"今晚不再提醒"/"紧急事项"激活**：暂停状态机，但保留已记录数据，次日重置

### 4.2 全屏弹窗提醒

- **主方案**：`WindowManager` + `TYPE_APPLICATION_OVERLAY` 系统悬浮窗
- **降级方案**：若悬浮窗权限被拒，使用全屏透明 Activity 弹窗
- **弹窗内容**：
  - 当前时间 + 已超时长
  - 渐进式提醒文案
  - "我知道了" 按钮（关闭弹窗，等待下次检查）
  - "今晚不再提醒" 按钮（跳过今晚监测）
  - "有紧急事项" 按钮（跳过今晚监测并记录，见 4.3）
- **弹窗行为**：自动置顶、播放提示音（可选）、振动提醒（可选）
- **"今晚不再提醒" 行为定义**：
  - 点击后写入 DataStore（记录跳过的日期 `skippedDate: String`），今晚立即停止所有提醒
  - 次日以"监测开始时间"为界自动重置，无需用户手动操作
  - 设置页中显示"今晚已跳过"状态，并提供手动恢复按钮
  - 跳过期间状态机暂停，不生成新的提醒记录
  - SleepRecord 中该晚记录正常生成，但 `sleepTime` 为 null，`sleepScore` 为 null（视为"未完成监测"）

### 4.3 紧急事项

- **触发方式**：弹窗中点击"有紧急事项"按钮
- **行为**：
  - 立即关闭弹窗，今晚停止所有监测提醒
  - 不记录具体原因，仅标记今晚发生了紧急事项
  - 与"今晚不再提醒"的区别：额外在 SleepRecord 中标记 `hasEmergency = true`
- **数据记录**：
  - SleepRecord 新增字段 `hasEmergency: Boolean`（默认 false）
  - 点击当晚 `hasEmergency` 置为 true
  - 统计页面中可查看哪些夜晚触发了紧急事项（以特殊图标或颜色标记）
- **重置逻辑**：与"今晚不再提醒"一致，次日以监测开始时间为界自动重置
- **评分影响**：紧急事项当晚的 SleepRecord `sleepScore` 为 null（视为"未完成监测"），不纳入连续早睡计算

### 4.4 渐进式提醒消息系统

- **默认5级消息**，用户可自定义每级的标题和内容：
  - 第1次：温和提醒
  - 第2次：关切提醒
  - 第3次：严肃提醒
  - 第4次：警告提醒
  - 第5次：最强提醒
- **循环机制**：5次后从第1次重新开始
- **配置能力**：用户可增删改每级消息，支持设置消息数量

### 4.5 可配置参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| 检测开始时间 | 00:00 | 到达此时间后开始监测 |
| 检测间隔 | 10 分钟 | 每隔多久检查一次屏幕状态 |
| 连续息屏次数阈值 | 3 次 | 连续多少次检测为息屏则认定入睡 |
| 提醒消息列表 | 5级预设 | 可自定义每级标题、内容、附带的健康知识 |
| 提醒音效 | 开/关 | 是否播放提示音 |
| 振动 | 开/关 | 是否振动 |
| 目标就寝时间 | 23:00 | 用于计算睡眠评分 |

### 4.6 睡眠记录与统计

**记录内容**（每晚一条记录）：
- 日期、监测开始时间、首次提醒时间、入睡时间、总提醒次数、亮屏检查次数、睡眠评分

**统计视图**：
- **周视图**：柱状图展示每天入睡时间，折线展示目标线
- **月视图**：日历热力图，颜色深浅代表入睡早晚
- **年视图**：月均入睡时间趋势线
- **趋势分析**：平均入睡时间变化、提醒次数变化、目标达成率

### 4.7 睡眠评分算法

```
基础分 = 100
扣分：每分钟晚于目标时间 -2 分，每次弹窗提醒 -3 分
加分：连续早睡天数 +1/天（上限 +20）
最终评分 = clamp(基础分 - 扣分 + 加分, 0, 100)
```

- **"早睡"定义**：在目标就寝时间前入睡即为早睡
- **"连续"计算**：按自然日连续，中间中断（包括"今晚不再提醒"/"紧急事项"当晚）则重置计数
- **未完成监测**："今晚不再提醒"和"紧急事项"当晚的 SleepRecord `sleepScore` 为 null，不参与连续计算

### 4.8 成就系统

| 成就 | 条件 |
|------|------|
| 初次早睡 | 第一次在目标时间前入睡 |
| 坚持一周 | 连续7天在目标时间前入睡 |
| 坚持一月 | 连续30天在目标时间前入睡 |
| 自律达人 | 连续90天在目标时间前入睡 |
| 低提醒周 | 一周内总提醒次数 ≤ 5 |
| 完美评分 | 单日睡眠评分达到 100 |
| 早睡冠军 | 连续7天评分 ≥ 90 |

- **进度计算**：实时查询 SleepRecord 表统计，不额外维护计数器（Achievement.currentProgress 作为缓存，定期同步）
- **解锁反馈**：首次达成时弹出 Material3 动画 + Toast 提示，可选播放音效
- **解锁时间**：记录到 `Achievement.unlockedAt`，成就页按解锁时间排序展示

### 4.9 开机自启动

- 注册 `BOOT_COMPLETED` 广播接收器
- 开机后检查：如果当前时间在监测窗口内，自动启动 `MonitorService`
- HyperOS 特殊处理：首次启动时引导用户到系统设置中开启自启动权限

### 4.10 通知渠道

| 渠道 ID | 名称 | 优先级 | 用途 |
|---------|------|--------|------|
| `monitor_channel` | 睡眠监测 | IMPORTANCE_LOW | 前台服务常驻通知，不弹出不发声 |
| `alert_channel` | 入睡提醒 | IMPORTANCE_HIGH | 全屏弹窗提醒，支持横幅通知和声音 |

- 前台服务通知必须始终可见，用户不可关闭（可通过设置页停止服务来消除）
- 提醒通知在弹窗触发时同步发送，确保用户即使不在 App 内也能感知

### 4.11 数据管理

- **数据保留**：历史数据默认永久保留，本地存储无容量限制
- **手动清除**：设置页提供"清除所有数据"功能，需二次确认对话框
- **数据库版本**：初始 version = 1，启用 `exportSchema = true` 导出 Schema JSON
- **迁移策略**：后续版本使用 Room `Migration(fromVersion, toVersion)` 增量迁移，不使用 `fallbackToDestructiveMigration`（避免用户数据丢失）

---

## 5. 数据库设计

### Entity: SleepRecord

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long (PK) | 自增主键 |
| date | String | yyyy-MM-dd，唯一约束 |
| monitorStartTime | Long | 监测开始时间戳 |
| firstAlertTime | Long? | 首次提醒时间戳 |
| sleepTime | Long? | 入睡时间戳 |
| totalAlertCount | Int | 总提醒次数 |
| screenOnCheckCount | Int | 亮屏检查次数 |
| sleepScore | Float? | 睡眠评分 |
| hasEmergency | Boolean | 今晚是否触发了紧急事项（默认 false） |
| createdAt | Long | 创建时间 |

### Entity: AlertMessage

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long (PK) | 自增主键 |
| level | Int | 1-based 级别 |
| title | String | 标题 |
| content | String | 内容 |
| healthTip | String | 健康知识 |
| isEnabled | Boolean | 是否启用 |

### Entity: Achievement

| 字段 | 类型 | 说明 |
|------|------|------|
| type | String (PK) | 成就类型标识 |
| unlockedAt | Long? | 解锁时间 |
| currentProgress | Int | 当前进度 |

### DataStore (Preferences)

监测开始时间、检测间隔、息屏阈值、目标就寝时间、音效开关、振动开关、服务启用状态、今晚已跳过日期（`skippedDate`）、今晚紧急事项标记（`emergencyDate`）

---

## 6. 权限清单

- `RECEIVE_BOOT_COMPLETED` — 开机自启动
- `SYSTEM_ALERT_WINDOW` — 悬浮窗
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` — 前台服务
- `POST_NOTIFICATIONS` — 通知
- `WAKE_LOCK` — 唤醒锁
- `VIBRATE` — 振动
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — 电池优化白名单
- `SCHEDULE_EXACT_ALARM` — 精确闹钟

**Android 版本适配说明**：

| 版本 | 权限 | 处理方式 |
|------|------|----------|
| Android 12+ (API 31) | `SCHEDULE_EXACT_ALARM` | 需用户在系统设置中手动授权，Setup 引导页中检测并引导；未授权时降级使用 `setAndAllowWhileIdle()` |
| Android 13+ (API 33) | `POST_NOTIFICATIONS` | 需运行时动态请求，Setup 引导页中统一处理 |
| Android 14+ (API 34) | `FOREGROUND_SERVICE_SPECIAL_USE` | Manifest 中必须显式声明 `foregroundServiceType` |

---

## 7. HyperOS / MIUI 特殊适配

1. 自启动权限：引导用户在 设置 → 应用管理 → SleepWatch → 自启动 中手动开启
2. 电池优化白名单：`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 引导关闭电池优化
3. 锁定后台任务：引导用户在最近任务列表中下拉锁定 App
4. 省电策略：设为"无限制"
5. MIUI 后台限制弹窗：首次启动时检测并提示

---

## 8. 低功耗设计策略

- 用 `BroadcastReceiver` 监听屏幕事件（系统回调，零功耗），不轮询
- AlarmManager 仅在检测间隔到达时唤醒，其余时间 CPU 完全休眠
- 前台服务最小化：通知栏常驻但低优先级，不执行周期性计算
- Room 查询使用索引，按需加载（Flow 懒加载）
- Compose 使用 `remember`、`derivedStateOf` 避免不必要重组
- 通知渠道使用 IMPORTANCE_LOW 避免前台服务通知打扰用户
- "今晚不再提醒"和"紧急事项"状态写入 DataStore（持久化），避免内存丢失后服务重启导致重复提醒

---

## 9. 开发阶段

| Phase | 内容 |
|-------|------|
| Phase 1 — 核心监测 (MVP) | 前台服务 + 屏幕检测 + AlarmManager + 全屏弹窗 + 基础设置 + Room |
| Phase 2 — 消息与配置 | 渐进式消息系统 + 可配置参数 + 权限引导 + 开机自启动 |
| Phase 3 — 统计与分析 | 周/月/年统计图表 + 趋势分析 + 睡眠评分算法 |
| Phase 4 — 成就与打磨 | 成就系统 + UI 精细化 + 真机测试 + 性能优化 |

---

## 10. 假设与默认值

- 目标设备为红米 K90 Pro Max / HyperOS 2.x / Android 15
- 用户的"一天"以监测开始时间（默认 00:00）为分界
- 默认连续 3 次息屏即认定入睡
- 睡眠评分目标就寝时间默认 23:00
- 不涉及云同步，所有数据本地存储
- 不涉及穿戴设备联动
- 提醒消息默认中文

---

## 11. 测试策略

| 层级 | 测试内容 | 工具 |
|------|----------|------|
| 单元测试 | UseCase 逻辑、Repository、睡眠评分算法、状态机状态转换 | JUnit 4 + Mockk |
| 集成测试 | Room DAO 查询验证、DataStore 读写、Repository + Database 联动 | AndroidJUnit + Room in-memory DB |
| UI 测试 | 关键页面（Home、Alert、Settings）的 Compose 交互测试 | Compose Testing |

- 优先保证睡眠评分算法和状态机的单元测试覆盖
- Room 使用内存数据库进行 DAO 层集成测试，不依赖真机存储
