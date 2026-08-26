# SleepWatch 可选无障碍后台恢复增强设计

> 日期：2026-08-26
> 状态：已确认

## 背景

SleepWatch 当前使用前台 `MonitorService`、精确闹钟、开机广播和持久化监测会话保证夜间监测可靠性。用户从最近任务界面划掉应用后，现有设计依赖下一次精确闹钟恢复进程和监测服务；在 HyperOS 等厂商系统上，应用进程可能被更积极地清理，导致恢复延迟或失败。

本功能为个人安装场景增加一个可选的无障碍服务，作为额外的系统绑定恢复锚点。目标是提高从最近任务中划掉 SleepWatch 后继续监测的概率，不尝试绕过 Android 的“强行停止”状态，也不承诺抵抗所有厂商强杀策略。

## 目标

- 用户可选择开启“后台增强（无障碍服务）”。
- 从最近任务中划掉 SleepWatch 后，若睡眠监测已启用，应用及时执行一次状态对账。
- 对账复用现有监测会话、前台服务和闹钟架构，不创建第二套睡眠判断逻辑。
- 无障碍服务不读取、记录、上传或操作屏幕内容。
- 未开启或关闭无障碍服务时，现有监测和恢复机制保持不变。

## 非目标

- 不在系统设置中“强行停止”后自动恢复。
- 不保证在所有 HyperOS 版本或极端内存压力下永不被杀。
- 不读取其他应用的窗口、文字、控件树、按键或用户输入。
- 不执行全局动作、手势或自动点击。
- 不把睡眠判断、提醒展示或记录写入职责迁移到无障碍服务。
- 不将无障碍设为启用睡眠监测的必需权限。

## 方案选择

### 采用：最小化无障碍恢复锚点 + 即时精确闹钟

新增一个系统绑定的 `SleepWatchAccessibilityService`。它只在服务连接和任务移除时请求一次即时状态对账，实际恢复仍由现有 `MonitorAlarmReceiver` 和 `MonitorService.ACTION_RECONCILE` 完成。

该方案保留既有可靠性架构，改动集中，且无障碍未授权时不会影响基础功能。

### 未采用：仅在普通服务中处理任务移除

可在 `MonitorService.onTaskRemoved()` 中安排恢复闹钟，权限更少、实现更简单，但当厂商系统连同应用任务一起清理普通进程时，没有额外的系统绑定组件作为恢复入口，不能满足本次增强目标。

### 未采用：由无障碍服务直接承担监测

把屏幕状态判断、调度和提醒迁移到无障碍服务会重复现有职责，扩大敏感 API 使用范围，并增加状态一致性和维护成本。

## 架构与组件

### `SleepWatchAccessibilityService`

职责：

- 由用户在系统无障碍设置中显式启用。
- 在 `onServiceConnected()` 中请求一次恢复对账，以覆盖首次开启、系统重连和重启后重连。
- 在 `onTaskRemoved()` 中请求一次恢复对账，以覆盖用户从最近任务划掉应用。
- `onAccessibilityEvent()` 不读取或处理事件内容。
- `onInterrupt()` 不执行恢复之外的业务逻辑。
- 使用与服务生命周期绑定的协程作用域；销毁时取消未完成任务。

该服务不直接判断当前是否在监测窗口，不访问睡眠记录，也不展示提醒。

### `AccessibilityServiceStatusChecker`

职责：

- 通过系统 `AccessibilityManager` 和组件名查询 SleepWatch 无障碍服务的真实启用状态。
- 为权限引导页和设置页提供状态。
- 不在 DataStore 保存一个可能与系统状态不一致的授权布尔值。

### `AccessibilityRecoveryCoordinator`

职责：

- 读取现有 `SettingsDataStore.serviceEnabled`。
- 仅当睡眠监测已启用时，请求即时对账。
- 把“服务已连接”和“任务已移除”归一成同一个幂等恢复动作。
- 捕获读取设置和请求调度的异常，避免无障碍服务崩溃。

该边界用于隔离系统回调、用户配置和闹钟调度，便于使用替身进行单元测试。

### `MonitorAlarmScheduler` 与 `MonitorAlarmReceiver`

扩展现有调度器：

- 新增独立的 `ACTION_RECONCILE` 和请求码。
- `scheduleReconcile()` 安排一次接近当前时间的精确闹钟。
- 重复调用更新同一个 `PendingIntent`，不会累积多个恢复闹钟。
- 安排即时对账时不调用 `cancelAll()`，避免先删除现有窗口开始、检查和结束闹钟。
- 接收即时对账后，将动作映射到 `MonitorService.ACTION_RECONCILE`。

精确闹钟属于 Android 允许在后台发起用户请求动作的前台服务启动入口。SleepWatch 已将精确闹钟作为启用监测的必需权限，因此该入口与现有权限模型一致。

### 现有 `MonitorService`

不新增第二套恢复逻辑。现有 `reconcileAndRunIfDue()` 继续作为唯一对账入口：

- 监测已关闭：取消调度并停止。
- 必需权限缺失：停用监测并提示用户。
- 当前在监测窗口：恢复或创建会话、注册设备广播、恢复前台通知并执行到期检查。
- 当前不在监测窗口：安排下一个窗口开始闹钟并停止前台服务。

数据库会话和现有触发校验继续保证重复恢复的幂等性。

## 数据流

### 用户开启后台增强

1. 用户在权限引导页点击“开启”。
2. 应用先显示用途与隐私披露。
3. 用户确认后进入系统无障碍设置并手动启用 SleepWatch。
4. 系统连接 `SleepWatchAccessibilityService`。
5. 恢复协调器确认睡眠监测已启用后安排即时对账闹钟。
6. 闹钟接收器启动 `MonitorService.ACTION_RECONCILE`。
7. `MonitorService` 根据当前时间和持久化状态恢复监测或安排下一窗口。

### 用户划掉最近任务

1. 系统向仍绑定的无障碍服务调用 `onTaskRemoved()`。
2. 恢复协调器读取睡眠监测开关。
3. 监测已启用时更新唯一的即时对账闹钟；监测已关闭时不动作。
4. 闹钟触发后由现有服务完成状态对账。

### 用户关闭无障碍服务

1. 系统解绑无障碍服务。
2. SleepWatch 不更改睡眠监测开关，不显示错误通知。
3. 后续继续依赖现有精确闹钟、开机广播、应用启动和前台服务恢复。

## 无障碍配置与隐私边界

Manifest 按 Android 无障碍服务契约设置 `android:exported="true"`，同时强制使用签名级系统权限 `android.permission.BIND_ACCESSIBILITY_SERVICE`，因此只有系统可以绑定该服务。无障碍 XML 元数据使用最小配置：

- 仅订阅 SleepWatch 自身包名的窗口状态事件，以满足服务配置要求；事件回调保持空实现。
- 不请求读取窗口内容。
- 不请求触摸探索、手势、按键过滤或增强 Web 无障碍能力。
- 不声明为面向残障用户的通用无障碍工具。
- 服务描述明确写明仅用于最近任务划掉后的监测恢复。

服务显式设置 `android:stopWithTask="false"`。现有 `MonitorService` 继续使用 `START_NOT_STICKY`，且不增加普通服务的 `onTaskRemoved()` 自启动逻辑。

无障碍回调不得访问 `AccessibilityNodeInfo`，不得根据其他应用事件触发睡眠判断，不新增任何无障碍数据持久化或网络传输。

## 用户界面

### 权限引导页

新增一张可选权限卡：

- 名称：`后台增强（无障碍服务）`
- 说明：`用于在最近任务中划掉 SleepWatch 后恢复睡眠监测`
- 未启用状态：`未开启（可选）`
- 已启用状态：`已开启`
- 未启用时提供“开启”按钮。

该卡不影响“完成”按钮和 `allRequiredGranted()`；无障碍始终是推荐能力，而不是必需权限。

点击“开启”先显示独立披露对话框：

> SleepWatch 使用无障碍服务，仅用于在应用被划出最近任务后触发后台监测恢复。不会读取、记录、上传或操作屏幕内容。

用户确认后打开系统无障碍设置；取消则保持现状。页面在 `ON_RESUME` 时重新读取系统授权状态。

### 设置页

保留现有“权限引导”入口，并在副标题中附加后台增强状态，例如：

- `检查所需权限；后台增强已开启`
- `检查所需权限；后台增强未开启`

设置页同样在 `ON_RESUME` 时刷新后台增强状态。睡眠监测关闭但无障碍仍开启时，无障碍服务保持空闲，不触发恢复。

## 异常处理

- 读取 `serviceEnabled` 失败：记录日志并结束本次恢复请求。
- 安排精确闹钟抛出 `SecurityException`：记录日志，不崩溃、不修改用户设置。
- 精确闹钟权限意外撤销：仅当应用已进入电池优化白名单时，允许尝试直接启动 `MonitorService.ACTION_RECONCILE`；该条件是 Android 官方列出的后台前台服务启动例外。
- 直接启动被系统拒绝：捕获 `ForegroundServiceStartNotAllowedException` 或兼容异常，记录日志并等待现有恢复入口，不循环重试。
- 重复连接、重复任务移除或短时间内连续回调：更新相同 `PendingIntent`，最终只保留一次即时对账。
- 无障碍被系统中断或关闭：不提示错误，不停用睡眠监测。
- 强行停止：不安排恢复；Android 15 及以上进入 stopped state 后系统还会取消应用待处理的 `PendingIntent`，必须由用户重新打开应用。

即时对账闹钟使用当前时间后一秒作为触发点，避免把已经到期的时间交给不同厂商的 `AlarmManager` 实现。无障碍回调本身不是成功保证：若厂商系统不投递 `onTaskRemoved()` 或连系统绑定服务也一起清理，仍只能依赖既有闹钟、开机广播或用户重新打开应用恢复。

## 测试设计

### 单元测试

- 睡眠监测开启时，无障碍连接会请求一次恢复。
- 睡眠监测开启时，任务移除会请求一次恢复。
- 睡眠监测关闭时，两种回调均不请求恢复。
- 设置读取失败时不请求恢复且不向外抛出异常。
- 重复恢复使用同一调度身份，不产生多个独立动作。
- 现有监测权限策略仍不把无障碍列入 `missingRequired`。

### 构建与回归

- 运行完整 JVM 单元测试。
- 编译 Android instrumentation tests。
- 构建 Debug APK。
- 运行 Android Lint，确认无新增错误。
- 回归启用/关闭睡眠监测、精确闹钟调度、开机恢复和提醒展示。

### HyperOS 真机验收

- 开启睡眠监测和后台增强，在监测窗口内划掉最近任务；前台通知应保持或自动恢复，下一次检查继续执行。
- 在非监测窗口划掉最近任务；应用不应长期显示监测通知，但下一窗口开始闹钟仍存在并可启动监测。
- 关闭后台增强后重复测试；应用继续使用原有精确闹钟恢复。
- 睡眠监测关闭但无障碍开启时划掉任务；不得启动监测或常驻通知。
- 连续快速划掉/重开应用，不得创建重复会话或叠加闹钟。
- 在系统设置中强行停止；应用不得自行恢复，重新打开后应恢复调度。

## 文档更新

实施时同步更新：

- `docs/REQUIREMENTS.md`：说明可选无障碍增强、隐私边界、最近任务划掉恢复和强行停止限制。
- `docs/PROGRESS.md`：记录实现状态，并增加 HyperOS 真机验收项。

## 完成标准

- 用户能从权限引导页理解并开启后台增强。
- 系统状态能准确显示，无需应用维护授权副本。
- 最近任务划掉后能触发即时对账，且不破坏现有闹钟。
- 无障碍服务不处理或保存任何界面内容。
- 无障碍未开启、被关闭或异常时，基础监测行为不退化。
- 自动化验证通过，并在 HyperOS 2 / Android 15 真机完成划掉最近任务场景验收。

## 参考资料

- Android Developers: [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- Android Developers: [Create an accessibility service](https://developer.android.com/guide/topics/ui/accessibility/service)
- Android Developers: [Restrictions on starting a foreground service from the background](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- Android Developers: [Behavior changes: Apps targeting Android 15 or higher](https://developer.android.com/about/versions/15/behavior-changes-15)
