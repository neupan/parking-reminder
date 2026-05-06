# 停车缴费提醒

停车缴费提醒是一个自用优先的 Android 原生应用，用来记录小区临时停车时间、实时计算停车费用，并在费用即将增加前提前响铃提醒。

项目当前针对南京小区临时停车规则实现：入库后 1 小时内免费，超过 1 小时按 5 元计费，之后每 12 小时增加 5 元。应用会在下一次计费变化前提醒用户缴费离场，避免因为错过临界点多付费用。

## 当前状态

当前工程是单模块 Android 项目：

- Kotlin
- Jetpack Compose
- Room
- AlarmManager 精确闹钟
- BroadcastReceiver
- Foreground Service
- 本地 Room 数据库

应用数据只保存在本机，不依赖服务器，也不需要网络。

## 核心功能

- 一键开始泊车，记录当前入库时间
- 实时显示当前费用、停车状态和倒计时
- 在费用即将变化前自动提醒
- 支持已缴费覆盖周期：一次缴费后生成覆盖窗口，在窗口内再次入库会识别为已缴费覆盖
- 点击“已缴费出库”结束本次停车，并按本次费用生成覆盖窗口
- 停车期间启动前台监控服务，提高后台提醒可靠性
- 提醒到点后发送高优先级通知并播放闹钟铃声
- 支持选择系统闹钟铃声
- 监听开机、应用更新、系统时间变化、时区变化、精确闹钟权限恢复，并自动重新同步下一条提醒

## 计费规则

生产模式使用以下规则：

| 阶段 | 费用 |
| --- | --- |
| 入库后 1 小时内 | 0 元 |
| 满 1 小时后到 12 小时内 | 5 元 |
| 满 12 小时后到 24 小时内 | 10 元 |
| 之后每增加 12 小时 | +5 元 |

提醒时间：

| 计费节点 | 提醒时间 |
| --- | --- |
| 免费结束，开始计费 5 元 | 入库后 50 分钟 |
| 即将加费到 10 元 | 入库后 11 小时 50 分钟 |
| 即将加费到 15 元 | 入库后 23 小时 50 分钟 |
| 后续加费节点 | 每个 12 小时计费边界前 10 分钟 |

已缴费覆盖规则：

- 如果一次出库时产生费用，应用会从出库时间开始创建一个覆盖窗口。
- 生产模式覆盖窗口为 12 小时。
- 在覆盖窗口内再次开始泊车，会显示为“已缴费覆盖中”，当前费用为 0 元。
- 覆盖窗口结束后仍未出库，会重新进入计费阶段。

## 测试规则与正式规则

项目支持在 App 内运行时切换规则，不需要为了测试秒级提醒重新编译 APK。

- 正式规则：用于真实停车
- 测试规则：用于开发和真机调试，时间压缩到秒级/分钟级
- 规则选择会持久化保存，下次打开应用仍沿用上次选择
- 切换规则后，应用会立即重新计算当前状态，并重新注册下一条提醒

新安装时的默认值：

- `debug` 包默认进入测试规则
- `release` 包默认进入正式规则

进入方式：

1. 打开应用首页。
2. 找到“正式规则”或“测试规则”卡片。
3. 点击“切换到测试规则”或“切换到正式规则”。

快速测试规则：

| 配置 | DebugFast |
| --- | --- |
| 免费期 | 30 秒 |
| 计费周期 | 1 分钟 |
| 提前提醒 | 20 秒 |
| 缴费覆盖窗口 | 1 分钟 |

正式规则：

| 配置 | Production |
| --- | --- |
| 免费期 | 1 小时 |
| 计费周期 | 12 小时 |
| 提前提醒 | 10 分钟 |
| 缴费覆盖窗口 | 12 小时 |

## 使用方法

### 首次安装后

1. 打开应用。
2. 按系统弹窗授予通知权限。
3. 如果页面提示“提醒注册失败”或出现精确闹钟相关提示，点击页面里的设置入口，允许“闹钟和提醒”权限。
4. 在小米、红米、MIUI、HyperOS 等设备上，建议额外完成后台权限配置，见下方“后台提醒权限配置”。
5. 如需自定义铃声，点击铃声选择入口，选择系统闹钟铃声。

### 开始停车

1. 到达车库后点击“开始泊车”。
2. 应用会记录当前时间为入库时间。
3. 页面会显示当前状态、费用、下一次变化时间和倒计时。
4. 应用会自动安排下一条提醒，并启动“停车监控”前台通知。

### 收到提醒后

1. 到提醒时间，应用会发送“停车缴费提醒”通知。
2. 同时启动铃声服务播放闹钟铃声。
3. 可点击页面或通知中的停止按钮停止铃声。
4. 应用会自动安排下一条未来提醒。

### 缴费出库

1. 缴费并离场后，点击“已缴费出库”。
2. 应用会结束当前停车会话。
3. 如果本次停车产生费用，会创建一个已缴费覆盖窗口。
4. 如果你在覆盖窗口内再次入库，再次点击“开始泊车”即可自动识别覆盖状态。

## 需要的权限

应用在 Manifest 中声明了以下权限：

| 权限 | 用途 | 用户需要操作吗 |
| --- | --- | --- |
| `POST_NOTIFICATIONS` | Android 13 及以上发送提醒通知 | 首次打开时授予 |
| `SCHEDULE_EXACT_ALARM` | Android 12 及以上注册精确提醒 | 可能需要到系统设置中允许“闹钟和提醒” |
| `RECEIVE_BOOT_COMPLETED` | 手机重启后重新同步提醒 | 不需要单独授权 |
| `WAKE_LOCK` | 提醒广播触发后短时间保持 CPU 工作，避免处理过程被睡眠打断 | 不需要单独授权 |
| `FOREGROUND_SERVICE` | 停车期间运行前台监控服务 | 不需要单独授权 |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | 提醒响铃服务以前台服务方式播放铃声 | 不需要单独授权 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | 停车期间显示前台监控通知，提高国产系统后台可靠性 | 不需要单独授权 |

### 后台提醒权限配置

在原生 Android 上，`AlarmManager.setAlarmClock()` 可以在应用退到后台、进程被系统回收后触发提醒。但在 MIUI、HyperOS、Redmi、小米等系统上，任务管理器上划、后台清理、省电策略可能会额外限制应用。

如果你希望“上划任务卡片后仍能提醒”，建议配置：

1. 应用信息 -> 电量与性能/省电策略 -> 选择“无限制”
2. 应用信息 -> 自启动 -> 允许
3. 最近任务界面 -> 长按应用卡片 -> 锁定应用
4. 应用信息 -> 权限 -> 允许通知
5. 应用信息 -> 闹钟和提醒 -> 允许

已经在 Redmi K50 / Android 12 上验证：开启自启动和省电策略“无限制”后，上划任务卡片会触发 `ParkingMonitorService.onTaskRemoved()`，应用会重新注册下一条提醒，并能准时响铃。

需要注意：如果用户在系统设置里“强行停止”应用，或使用厂商清理工具做深度冻结，普通 Android 应用无法保证自启动和提醒投递。这是系统限制，不是业务代码可以完全绕过的行为。

## 提醒可靠性设计

当前提醒链路如下：

1. 用户开始停车后，`ReminderResyncService` 根据当前停车状态计算下一条 `ReminderPlan`。
2. `ReminderAlarmScheduler` 使用 `AlarmManager.setAlarmClock()` 注册精确闹钟。
3. 停车期间启动 `ParkingMonitorService`，并显示“停车监控”前台通知。
4. 如果用户从最近任务中划走应用，`ParkingMonitorService.onTaskRemoved()` 会重新同步并注册提醒。
5. 闹钟到点后，系统投递 `ParkingReminderReceiver`。
6. `ReceiverAsync` 使用 `goAsync()` 和短时 `PARTIAL_WAKE_LOCK` 完成异步处理。
7. `AndroidReminderNotifier` 发送高优先级通知，并启动 `AlarmSoundService` 播放铃声。
8. 处理完成后再次 `resync`，安排下一条提醒。

应用还会在以下系统事件后重新同步提醒：

- 开机完成
- App 更新完成
- 系统时间被修改
- 系统时区被修改
- 精确闹钟权限被重新授予
- App 冷启动

## 本地数据

应用使用 Room 数据库，数据库名为：

```text
parking_reminder.db
```

主要数据表：

- `active_parking_session`：当前活动停车会话
- `coverage_window`：已缴费覆盖窗口
- `parking_history`：停车历史记录
- `reminder_schedule_state`：当前提醒调度状态

默认历史记录上限为 50 条。

## 项目结构

```text
app/src/main/java/com/neupan/parking_reminder
├── alarm       # 闹钟调度、广播接收、通知、铃声、后台监控
├── data        # Room 数据库、DAO、Entity、Repository
├── domain      # 业务模型、计费规则、提醒规划、命令服务
├── ui          # Compose UI、首页状态和 ViewModel
├── AppContainer.kt
├── MainActivity.kt
└── ParkingReminderApp.kt
```

核心类：

| 类 | 作用 |
| --- | --- |
| `ParkingRuleConfig` | 定义正式/调试计费参数 |
| `BillingCalculator` | 计算当前停车状态和费用 |
| `ReminderPlanner` | 计算下一条提醒 |
| `ParkingStateResolver` | 汇总费用状态和提醒计划 |
| `ReminderResyncService` | 统一处理提醒重建、取消、触发后的再调度 |
| `ReminderAlarmScheduler` | 封装 `AlarmManager` 精确闹钟 |
| `ParkingReminderReceiver` | 接收闹钟到点广播 |
| `AndroidReminderNotifier` | 发送通知并启动铃声 |
| `AlarmSoundService` | 前台服务播放提醒铃声 |
| `ParkingMonitorService` | 停车期间前台监控和上划任务后的 resync |

## 开发环境

建议环境：

- Android Studio 最新稳定版
- JDK 17 或兼容 Android Gradle Plugin 的 JDK
- Android SDK Platform 36
- Kotlin 2.0.x

当前 Gradle 配置：

| 配置 | 值 |
| --- | --- |
| `minSdk` | 24 |
| `targetSdk` | 35 |
| `compileSdk` | 36 |
| `applicationId` | `com.neupan.parking_reminder` |
| `versionName` | `1.0` |

## 构建与测试

运行单元测试：

```bash
./gradlew testDebugUnitTest
```

构建 Debug APK：

```bash
./gradlew assembleDebug
```

构建 Release APK：

```bash
./gradlew assembleRelease
```

强制重新执行测试和 Debug 构建：

```bash
./gradlew testDebugUnitTest assembleDebug --rerun-tasks
```

Debug APK 输出路径通常为：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 调试提醒链路

可以用 `adb logcat` 观察提醒链路：

```bash
adb logcat -c
adb logcat | grep -E "AlarmScheduler|ReminderSync|ParkingReceiver|ReceiverAsync|Notifier|AlarmSoundSvc|ParkMonitorSvc"
```

重点日志：

| 日志 | 含义 |
| --- | --- |
| `AlarmScheduler: schedule() setAlarmClock called OK` | 精确闹钟已注册 |
| `ParkMonitorSvc: onTaskRemoved()` | 用户上划任务卡片后，服务收到回调 |
| `ParkingReceiver: onReceive()` | 系统到点投递提醒广播 |
| `ReceiverAsync: launchAsync() ... wakeLock=true` | 广播异步处理期间已持有 wake lock |
| `Notifier: showReminder()` | 通知逻辑执行 |
| `AlarmSoundSvc: playRingtone() playing=true` | 铃声实际开始播放 |
| `Exact alarm capability is not available` | 精确闹钟权限不可用，需要去系统设置授权 |

## 已知限制

- 真正“强行停止”应用后，Android 不允许普通应用自行唤醒，提醒可能无法触发。
- 部分国产系统会把最近任务上划、一键清理、深度省电做成接近冻结应用的行为，需要用户手动开启自启动和省电无限制。
- `SCHEDULE_EXACT_ALARM` 属于敏感能力；如果未来上架应用商店，需要重新评估平台政策。
- 当前只有单车/单会话模型，不支持多辆车同时停车。
- 当前不做云同步，卸载应用会删除本地数据。

## 相关文档

- [PRD.md](./PRD.md)
- [ANDROID_TECH_PLAN.md](./ANDROID_TECH_PLAN.md)
- [ANDROID_DEV_BLUEPRINT.md](./ANDROID_DEV_BLUEPRINT.md)
