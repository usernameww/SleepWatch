# SleepWatch 苹果版需求与技术设计

> 日期：2026-07-31
> 状态：已确认
> 范围：不依赖 Apple Watch、不要求用户回填、仅个人开发或内部安装

## 1. 文档目标

本文分析现有 Android 项目的产品能力和技术实现，并定义一个符合 iOS 平台能力边界的苹果版本。

苹果版本延续 SleepWatch 的核心目的：在目标就寝时间后自动监督手机使用，并对晚睡行为主动提醒。它不尝试通过 iPhone 判断生理睡眠，也不把“未检测到手机使用”描述成“已经入睡”。

## 2. 现有 Android 项目分析

### 2.1 产品目标

当前 SleepWatch 在用户配置的夜间窗口内定时检查手机是否仍在使用：

- 手机亮屏且已解锁时，判定用户仍在使用，展示休息提醒。
- 连续多次检测到手机未使用时，将第一次未使用的检测时刻推断为入睡时间。
- 记录每晚监测过程，并生成评分、统计和成就。

项目主要面向 HyperOS 2 / Android 15，同时兼容 Android API 26 及以上。

### 2.2 功能模块

当前应用包含四个主页面和两个辅助页面：

1. 首页
   - 展示时间、日期、监测状态和服务开关。
   - 展示最近一次睡眠记录、评分、入睡时间和提醒次数。
   - 显示监测窗口及权限异常。
2. 统计
   - 支持周、月、年周期。
   - 计算平均入睡时间、平均评分、目标达成次数和达成率。
   - 展示睡眠趋势和详细历史记录。
3. 成就
   - 初次早睡。
   - 连续 7、30、90 天达标。
   - 一周低提醒。
   - 单日满分。
   - 连续高分。
4. 设置
   - 启用或关闭监测服务。
   - 配置监测开始时间、结束时间、检测间隔、连续未使用阈值和目标就寝时间。
   - 配置声音、振动和渐进提醒文案。
   - 进入权限引导。
   - 清除历史、成就及提醒文案。
5. 权限引导
   - 通知权限。
   - 悬浮窗权限。
   - 精确闹钟权限。
   - 电池优化和 HyperOS 自启动引导。
6. 提醒文案编辑
   - 新增、编辑、启用、停用和删除提醒消息。

### 2.3 Android 监测算法

当前业务规则由独立领域层实现：

- `MonitoringWindowResolver` 负责普通、跨午夜、跨月和跨年窗口。
- `MonitoringEngine` 负责使用中、未使用、解锁打断、结束、跳过和配置变更。
- `MonitoringTriggerValidator` 保证重复触发幂等。
- `SleepScoreCalculator` 计算睡眠评分。
- `SleepStatisticsCalculator` 计算统计指标。

设备“使用中”的判断是：

```text
PowerManager.isInteractive
&& !KeyguardManager.isDeviceLocked
```

达到连续未使用阈值后，系统将第一次未使用检查时刻保存为推断入睡时间。当窗口结束仍未确认时，记录 `INCOMPLETE`，不使用窗口结束时间伪造入睡。

### 2.4 Android 调度与提醒

- `AlarmManager` 负责窗口开始、周期检测和窗口结束。
- 窗口内使用前台服务，窗口外只保留下次计划。
- 进程被回收后可由下一次闹钟恢复。
- 亮屏且解锁时使用系统悬浮窗提醒。
- 悬浮窗失败时降级为高优先级通知。
- 开机、应用更新、系统时间和时区变化后进行计划对账。

### 2.5 数据与架构

当前 Android 技术栈：

- Kotlin 2
- Jetpack Compose
- Hilt
- Room v2
- DataStore
- Coroutines
- Vico Charts

项目采用 UI、领域、数据和系统服务分层。Room 中的 `SleepRecord` 保存周期、窗口、目标时间、检测次数、提醒次数、状态、评分和恢复字段。

当前 JVM 单元测试已通过。项目还包含 Room 迁移测试和悬浮窗仪器测试，真机夜间可靠性仍是发布前主要验收项。

## 3. iOS 平台能力差异

### 3.1 无法等价迁移的能力

iOS 普通应用没有公开 API 可以：

- 周期读取设备是否亮屏和解锁。
- 像 Android 前台服务一样整夜常驻并每隔数分钟执行。
- 在其他应用之上显示任意全屏悬浮窗。
- 将没有事件回调直接解释成用户已经睡着。

因此，Android 的“解锁轮询 + 连续未使用确认 + 悬浮提醒”不能一比一迁移。

### 3.2 可用于苹果版本的系统能力

苹果版本使用 Screen Time 技术体系：

- Family Controls：请求个人设备授权。
- Device Activity：监测日程内的前台应用、类别和网站使用时间。
- Device Activity Monitor Extension：在窗口开始、结束或累计使用达到阈值时接收系统回调。
- User Notifications：发送本地提醒。
- App Group：在主 App 与扩展之间共享配置和监测事件。

`DeviceActivityEvent` 统计应用、类别和网站处于屏幕前台的累计时间。达到配置阈值时，系统调用扩展的 `eventDidReachThreshold`。它能证明目标就寝时间后仍存在手机使用，但不能证明用户已经睡着。

### 3.3 产品口径调整

苹果版本的检测对象定义为：

> 目标就寝时间后，是否仍存在设备前台使用行为。

苹果版本不展示“检测到入睡时间”，而使用以下表述：

- 未检测到晚睡使用。
- 检测到至少 10 分钟晚睡使用。
- 首次晚睡提醒发生于 23:41。
- 当晚监测不可用。

## 4. 已确认的产品决策

- 不依赖 Apple Watch。
- 不读取 HealthKit。
- 不要求用户手动填写或确认睡眠状态。
- 只发送通知，不限制或拦截应用。
- 仅在检测到目标时间后仍有手机使用时通知。
- 监测全设备前台使用，不要求用户选择应用。
- 首次累计使用达到可配置阈值时提醒，默认 1 分钟。
- 首次提醒后按可配置累计使用间隔重复提醒，默认每 10 分钟。
- 目标就寝时间就是监测开始时间。
- 监测结束时间单独配置。
- 数据只保存在本机，不提供账号、服务器或 Android 同步。
- 首期只用于个人开发测试或内部安装，不申请 App Store 分发。
- 保留首页、统计、成就和设置四个主页面。

## 5. 配置需求

### 5.1 可配置项

| 配置项 | 默认值 | 合法范围 |
|---|---:|---:|
| 自动监督 | 关闭 | 开启或关闭 |
| 目标就寝时间 | 23:00 | 任意本地时间 |
| 监测结束时间 | 05:00 | 任意本地时间，支持跨午夜 |
| 生效星期 | 每天 | 周一至周日多选 |
| 首次提醒阈值 | 1 分钟 | 1–30 分钟 |
| 重复提醒间隔 | 10 分钟 | 5–60 分钟 |
| 最大提醒次数 | 持续到窗口结束 | 1–48 次或持续到结束 |
| 通知声音 | 开启 | 开启或关闭 |
| 渐进提醒文案 | 内置默认值 | 可编辑、启用或停用 |

iOS 通知振动跟随系统通知和声音设置，应用不提供无法可靠兑现的独立振动开关。

### 5.2 配置校验

- 监测窗口限制为 30 分钟至 12 小时。
- 每晚最多生成 48 个 Device Activity 阈值事件。
- 超过事件上限时拒绝保存，并提示增大重复间隔或缩短窗口。
- 最大提醒次数只限制通知数量，不停止后续活动监测和事件记录。
- 关闭自动监督立即停止当前计划。
- 其他配置在监测窗口内修改后，从下一晚开始生效。
- 每晚使用不可变配置快照，避免过程中修改导致统计口径变化。

### 5.3 阈值生成

默认配置示例：

```text
目标就寝时间：23:00
监测结束时间：05:00
首次提醒阈值：1 分钟
重复提醒间隔：10 分钟

阈值：1、10、20、30、40……分钟
```

每个阈值只触发一次。达到最大提醒次数后，后续阈值仍记录，但不发送通知。

## 6. 功能需求

### 6.1 首次设置与权限

- 请求 `FamilyControlsMember.individual` 授权。
- 请求通知权限。
- 引导用户完成初始监测配置。
- 两项核心权限均可用后才能启用自动监督。
- 权限异常时提供原因和系统设置入口。

### 6.2 首页

- 自动监督开关。
- 今晚或下一次监测窗口。
- 当前状态：等待开始、监测中、已完成、已关闭、权限异常。
- 当前配置摘要和生效时间。
- 今晚已触发提醒次数。
- 最近一晚监督结果和评分。
- 计划版本、授权状态和最近系统回调等诊断摘要。

### 6.3 自动夜间记录

记录状态：

- `NO_LATE_USE_DETECTED`：监测健康且没有达到首次使用阈值。
- `LATE_USE_DETECTED`：达到至少一个使用阈值。
- `MONITORING_UNAVAILABLE`：授权、计划或扩展状态不可靠。
- `DISABLED`：自动监督关闭或当天不在生效星期。
- `ENDED_BY_CONFIG_CHANGE`：监测窗口内被用户关闭。

每晚记录：

- 周期日期。
- 配置快照和配置版本。
- 监测开始及结束时间。
- 首次晚睡使用回调时间。
- 达到的最高累计使用阈值。
- 提醒次数。
- 每个阈值事件和通知结果。
- 授权、计划和扩展健康状态。

主 App 不要求用户补填。窗口结束事件或主 App 下次进入前台时自动完成记录。

### 6.4 统计

支持周、月、年周期，展示：

- 有效监测天数。
- 未检测到晚睡使用的天数和比例。
- 检测到晚睡使用的天数。
- 累计晚睡使用档位。
- 平均提醒次数。
- 连续按时记录。
- 趋势图和每晚详细记录。

Device Activity 阈值是离散数据。最高达到 20 分钟阈值且未达到 30 分钟时，界面显示“估计累计使用 20–29 分钟”，不显示伪精确值。

`MONITORING_UNAVAILABLE` 不参与达标率、平均值、评分或成就。

### 6.5 评分

首版采用固定评分规则：

```text
无晚睡使用：100
最高阈值 1 分钟：98
最高阈值 10 分钟：80
最高阈值 20 分钟：60
最高阈值 30 分钟：40
最高阈值 40 分钟：20
最高阈值 50 分钟及以上：0
```

监测不可用时评分为空。评分仅用于行为反馈，不代表医学睡眠质量。

### 6.6 成就

- 初次按时。
- 连续 7、30、90 天未检测到晚睡使用。
- 一周低使用。
- 单日满分。
- 连续 7 天评分不低于 90。
- 连续多晚无需提醒。

### 6.7 设置

- 编辑全部监测配置。
- 编辑渐进提醒文案。
- 查看并修复权限。
- 查看计划和扩展诊断状态。
- 发送测试通知。
- 清除历史和成就。
- 恢复默认提醒文案。

## 7. 系统架构

### 7.1 目标与边界

主 App 负责配置、计划注册、数据导入、记录计算和界面展示。系统和监测扩展负责夜间活动监测。主 App 不需要整夜运行。

```text
SwiftUI 主 App
  ├── 配置与权限
  ├── DeviceActivity 计划注册
  ├── SwiftData 本地数据库
  ├── 统计与成就
  └── App Group 事件导入
               │
               ▼
         App Group
  ├── 不可变配置快照
  ├── 监测事件收件箱
  └── 监测健康状态
               ▲
               │
DeviceActivityMonitor Extension
  ├── 阈值回调
  ├── 窗口回调
  ├── 事件原子写入
  └── 本地通知
               ▲
               │
      iOS Device Activity
```

### 7.2 Xcode Target

1. `SleepWatchIOS`
   - SwiftUI 页面。
   - 配置、权限和调度。
   - SwiftData。
   - 统计、评分和成就。
2. `SleepWatchMonitorExtension`
   - `DeviceActivityMonitor` 子类。
   - 阈值和窗口事件处理。
   - App Group 事件写入。
   - 本地通知。

首版不增加 Shield、Shield Action 或 Device Activity Report Extension。

### 7.3 推荐技术栈

- 最低系统：iOS 17。
- Swift 6。
- SwiftUI。
- SwiftData。
- Swift Concurrency。
- FamilyControls。
- DeviceActivity。
- UserNotifications。
- App Groups。
- Swift Charts。

## 8. 核心组件设计

### 8.1 `MonitoringConfigRepository`

负责配置的读取、校验、版本化和不可变快照生成。

### 8.2 `MonitoringWindowResolver`

输入本地时间、目标时间、结束时间和星期配置，输出：

- 周期日期。
- 具体开始时刻。
- 具体结束时刻。
- 当前是否属于有效监测日。

跨午夜窗口归属于开始日期。

### 8.3 `ThresholdPlanGenerator`

根据窗口长度、首次阈值、重复间隔和 48 个事件上限生成阈值列表。生成结果必须确定且可单元测试。

### 8.4 `MonitoringScheduleCoordinator`

- 检查 Family Controls 和通知权限。
- 将配置快照写入 App Group。
- 注册每日重复 Device Activity 计划和全部阈值事件。
- 停止旧计划后再提交新计划。
- 注册失败时保留上一份有效配置和计划。
- App 进入前台时进行配置、授权和系统计划对账。

为避免七套高密度事件，只维护一套每日重复计划。扩展依据配置快照判断周期开始日是否属于启用星期；非启用日记录为 `DISABLED`，不发送通知。

全设备活动事件使用空的应用、类别和网站集合，使 `includesAllActivity` 为真。实施前真机验证必须确认该行为在目标 iOS 版本和个人授权模式下成立。

### 8.5 `SleepWatchMonitorExtension`

扩展处理：

- `intervalDidStart`。
- `eventDidReachThreshold`。
- `intervalDidEnd`。

阈值回调执行：

1. 读取配置快照。
2. 解析周期日期。
3. 判断当天是否启用。
4. 构造唯一事件。
5. 原子写入共享事件收件箱。
6. 在提醒上限内选择文案并发送通知。
7. 更新最近回调和健康状态。

扩展不进行复杂统计，不直接访问主 SwiftData 数据库。

### 8.6 `SharedEventInbox`

每个事件使用独立 Codable 文件。写入过程先创建临时文件，再原子重命名。

事件唯一键：

```text
cycleDate + configurationVersion + thresholdMinutes
```

窗口开始和结束事件使用固定事件类型参与唯一键。重复系统回调只保留一条。

### 8.7 `MonitoringEventImporter`

主 App 启动或进入前台时：

1. 扫描共享事件目录。
2. 按唯一键导入 SwiftData。
3. 更新夜间记录。
4. 成功提交数据库事务后删除共享事件文件。

导入失败时保留文件，下次重试。

### 8.8 `NightRecordFinalizer`

根据配置快照、阈值事件、窗口结束和健康状态生成最终结果。缺少可靠监测证据时必须输出 `MONITORING_UNAVAILABLE`，不能输出 `NO_LATE_USE_DETECTED`。

### 8.9 `StatisticsCalculator` 与 `AchievementEvaluator`

采用纯 Swift 领域模型，不依赖 SwiftUI、SwiftData 或系统扩展，便于单元测试。

## 9. 数据模型

### 9.1 `MonitoringConfiguration`

- `id`
- `version`
- `enabled`
- `targetBedtime`
- `monitorEndTime`
- `activeWeekdays`
- `firstReminderThresholdMinutes`
- `repeatReminderIntervalMinutes`
- `maxReminderCount`
- `soundEnabled`
- `effectiveFromCycleDate`
- `updatedAt`

### 9.2 `NightRecord`

- `id`
- `cycleDate`
- `configurationVersion`
- `windowStartAt`
- `windowEndAt`
- `status`
- `firstLateUseCallbackAt`
- `highestReachedThresholdMinutes`
- `reminderCount`
- `score`
- `monitoringHealth`
- `finalizedAt`
- `createdAt`

### 9.3 `MonitoringEvent`

- `eventId`
- `cycleDate`
- `configurationVersion`
- `type`
- `thresholdMinutes`
- `callbackAt`
- `notificationAttempted`
- `notificationResult`
- `createdAt`

### 9.4 `ReminderTemplate`

- `id`
- `level`
- `title`
- `body`
- `isEnabled`
- `createdAt`
- `updatedAt`

### 9.5 `Achievement`

- `type`
- `currentProgress`
- `unlockedAt`

## 10. 通知设计

- 第一次达到阈值后立即请求本地通知。
- 后续达到累计阈值后，根据重复间隔继续通知。
- 文案按已发送提醒次数选择渐进级别。
- 某一级被停用时选择下一条可用文案。
- 所有文案均不可用时使用内置安全兜底文案。
- 通知声音由配置决定。
- 不使用 Time Sensitive 或 Critical Alert 作为首版默认能力。
- 通知发送结果与阈值事件分别记录，避免把通知失败误认为监测失败。

## 11. 状态与数据流

```text
配置完成
  → SCHEDULED
  → 窗口开始
  → MONITORING
      ├── 达到阈值 → LATE_USE_DETECTED → 后续阈值继续记录
      ├── 健康监测且无阈值 → NO_LATE_USE_DETECTED
      ├── 权限或计划异常 → MONITORING_UNAVAILABLE
      └── 用户关闭 → ENDED_BY_CONFIG_CHANGE
  → FINALIZED
```

`NO_LATE_USE_DETECTED` 的必要条件是：

- 配置有效。
- Family Controls 授权在窗口内有效。
- Device Activity 计划注册成功。
- 主 App 对账时系统仍包含预期计划。
- 没有已知的扩展、共享存储或计划异常。
- 没有达到首次阈值。

如果用户没有晚睡使用，系统可能不会产生任何阈值回调，因此“没有扩展回调”本身不是故障。监测健康主要依据授权状态、计划注册结果、系统计划对账和已知错误。

## 12. 异常处理

### 12.1 权限

- Family Controls 拒绝或撤销：停止计划，标记不可用。
- 通知权限拒绝或关闭：不能启用自动监督，引导用户修复。
- 权限恢复后：重新写入配置快照并注册计划。

### 12.2 计划

- `startMonitoring` 抛错：保留上一份有效配置，显示明确错误。
- 事件过密：配置校验阶段阻止提交。
- 系统计划和本地配置版本不一致：App 进入前台时重新注册。

48 个阈值是应用自身的安全上限，不是 Apple 公布的系统保证。最小真机验证必须覆盖目标窗口对应的最大事件量；若系统拒绝该密度，应用校验上限按验证结果下调。

### 12.3 回调与存储

- 重复回调：通过事件唯一键幂等处理。
- 扩展写入中断：依靠临时文件和原子重命名避免半文件。
- 主 App 导入失败：保留事件文件并重试。
- 通知发送失败：记录失败，但保留活动事件。

### 12.4 时间

- 时区或系统时间变化：主 App 下次进入前台时重新解析窗口并注册计划。
- 夏令时切换：以本地日历和具体周期日期解析，不直接假设一天固定为 24 小时。
- 回调延迟：阈值分钟是主要统计证据，回调时间只作为系统回调时间展示。

### 12.5 生命周期

- 主 App 退出后，已注册的 Device Activity 计划不依赖主 App 常驻。
- 授权撤销、App 卸载或系统拒绝扩展执行后不能继续监督。
- iOS 没有 Android `BootReceiver` 等价机制，App 每次进入前台都必须进行完整对账。

## 13. 隐私与分发

- 所有配置、事件和统计均保存在本机。
- 不使用账号、网络服务、第三方分析 SDK 或广告 SDK。
- 不读取 HealthKit。
- Screen Time 选择和活动数据只用于用户本人晚睡监督。
- 主 App 与扩展只通过自身 App Group 共享数据。
- 个人通过 Xcode 安装到已注册设备时使用 Family Controls 开发能力。
- 未来若使用 TestFlight、Ad Hoc、企业或 App Store 分发，需要重新核对签名方式并申请相应 Family Controls 分发权限。

## 14. 测试设计

### 14.1 单元测试

- 普通和跨午夜窗口。
- 跨月、跨年和夏令时边界。
- 星期与周期日期。
- 首次阈值边界。
- 重复间隔边界。
- 48 个事件上限。
- 配置版本和生效日期。
- 重复回调幂等。
- 事件导入幂等。
- 状态最终化。
- 评分、统计和成就。
- 渐进提醒文案循环和兜底。

### 14.2 集成测试

- App Group 配置快照。
- 事件原子写入和导入。
- SwiftData schema 迁移。
- 配置重新注册和旧计划停止。
- 通知权限变化。
- Family Controls 授权变化。
- 扩展写入失败恢复。

### 14.3 真机验收

- 就寝后累计使用 1 分钟触发首次提醒。
- 累计达到 10、20、30 分钟继续提醒。
- 停止使用后不产生固定时间误提醒。
- 锁屏整夜不发送晚睡提醒。
- 主 App 不在前台时扩展仍可记录事件。
- 重启后首次打开 App 能正确对账。
- 时区变化后计划重建。
- 授权撤销后不生成虚假达标记录。
- 通知关闭后不能继续宣称监督正常。
- 连续两个真实夜间周期自动运行。

Family Controls 和 Device Activity 的核心行为必须使用真机验证。

## 15. 实施前技术验证

正式开发完整 UI 前，先实现一个最小真机验证：

1. 请求个人 Family Controls 和通知授权。
2. 注册一个短窗口。
3. 注册 1、2、3 分钟全设备活动阈值。
4. 验证扩展回调。
5. 验证 App Group 原子事件写入。
6. 验证扩展发起本地通知。
7. 验证主 App 被结束后的行为。
8. 验证重复回调和计划重新注册。

只有真机验证通过后，才进入完整页面、统计和成就开发。这一验证将优先消除“全设备活动覆盖、扩展通知、后台回调及时性和事件数量限制”四项主要平台风险。

## 16. 非目标

首版不实现：

- 生理睡眠检测。
- Apple Watch 或 HealthKit。
- 用户手动回填睡眠状态。
- 应用 Shield 或强制限制。
- 云同步和 Android 数据同步。
- 账号体系。
- 推送服务器。
- Device Activity Report 扩展。
- App Store 正式发布。

## 17. 官方技术依据

- [Screen Time Technology Frameworks](https://developer.apple.com/documentation/ScreenTimeAPIDocumentation)
- [Family Controls](https://developer.apple.com/documentation/FamilyControls)
- [AuthorizationCenter](https://developer.apple.com/documentation/FamilyControls/AuthorizationCenter)
- [DeviceActivityCenter](https://developer.apple.com/documentation/deviceactivity/deviceactivitycenter)
- [DeviceActivitySchedule](https://developer.apple.com/documentation/deviceactivity/deviceactivityschedule)
- [DeviceActivityEvent](https://developer.apple.com/documentation/deviceactivity/deviceactivityevent)
- [DeviceActivityMonitor](https://developer.apple.com/documentation/deviceactivity/deviceactivitymonitor)
- [Configuring Family Controls](https://developer.apple.com/documentation/xcode/configuring-family-controls)
- [User Notifications](https://developer.apple.com/documentation/usernotifications)

## 18. 验收结论

在不依赖 Apple Watch、不要求用户回填、只进行通知提醒的约束下，苹果版本可以实现“目标就寝时间后自动检测持续手机使用并渐进提醒”的核心产品目标。

它不能复刻 Android 的亮屏解锁轮询，也不能可靠判断用户已经睡着。成功实现的关键是将产品指标改为“晚睡手机使用行为”，以 Device Activity 累计前台使用阈值作为证据，并对监测不可用和数据精度做诚实、可验证的处理。
