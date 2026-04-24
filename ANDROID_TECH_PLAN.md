# 停车提醒 Android 技术方案

## 1. 文档目的

本文档用于定义停车提醒项目的 Android 实现方案。

它基于当前 [PRD](./PRD.md) 和 2026-04-24 已确认的产品决策，重点解决以下问题：

- 用什么形态实现最稳
- 提醒链路怎么做才能接近闹钟级别
- 计费规则和覆盖周期如何落成代码
- 本地数据如何存储
- 项目结构如何规划，方便后续直接开工

这份文档不是最终 UI 视觉稿，而是后续开发的技术基线。

---

## 2. 已确认的产品边界

以下前提已确认，后续方案默认以此为准：

- App 目前仅供自己使用，先以本地安装 APK 为目标
- 暂时不考虑上应用商店
- 首版只做 Android
- 提醒强度要接近闹钟级别，而不是普通通知
- 如果已缴费的 12 小时覆盖周期结束时车辆仍在场内，立即进入下一轮计费阶段

这些前提带来的直接影响：

- 方案优先考虑真机可靠性，而不是跨平台统一
- 不再保留 Web 为主、Android 为辅的设计思路
- 可以优先采用最适合自用的 Android 原生能力，后续若要上架再单独评估政策约束

---

## 3. 核心技术结论

### 3.1 主实现方案

首版建议实现为原生 Android App，技术栈如下：

- Kotlin
- Jetpack Compose
- Room
- AlarmManager 精确闹钟
- BroadcastReceiver
- Notification Channel

### 3.2 不再作为主链路的方案

以下方案不建议再作为主提醒链路：

- 浏览器通知
- 静态网页 + 本地计时
- `.ics` 下载导入日历
- 跳系统日历创建事件
- WorkManager 负责精确时点提醒

原因很明确：

- 这个产品真正的核心能力是“在精确时点可靠提醒”，不是“生成一个日历事件”
- 你已经在红米 K50 上验证过 Web / 日历导入链路存在兼容性问题
- WorkManager 适合持久后台任务，不适合这种精确提醒场景

### 3.3 提醒机制的主决策

首版提醒链路建议采用以下顺序：

1. 用 `AlarmManager.setAlarmClock()` 注册下一条即将到来的提醒
2. 到点后通过 `BroadcastReceiver` 拉起高优先级提醒通知
3. 如真机行为稳定，再增加独立的提醒页或全屏提醒能力

推荐优先尝试 `setAlarmClock()` 的原因：

- 它更接近“闹钟级”用户心智
- 它本来就是面向用户可感知的精确时点提醒
- 对当前需求比普通后台任务 API 更匹配

保底策略：

- 如果目标设备对 `setAlarmClock()` 的表现不理想，再切换为 `setExactAndAllowWhileIdle()`
- 调度接口要抽象出来，避免以后替换时牵一发动全身

---

## 4. 当前工程基线与依赖建议

当前仓库已经有一个 Compose 空壳工程，基线如下：

- `minSdk = 24`
- `targetSdk = 35`
- `compileSdk = 36`
- Kotlin 2.0.x
- Compose 项目模板已就绪

MVP 建议补充的依赖：

- Room
- Lifecycle ViewModel
- Navigation Compose
- Coroutines
- 时间处理工具

可选但不是 MVP 必需：

- DataStore
- Hilt / Koin
- WorkManager

结论：

- MVP 保持单模块 `app` 即可
- 先不要引入 DI 框架
- 先把提醒可靠性和规则正确性跑通，再决定是否加架构复杂度

原因：

- 这个项目的复杂点在业务规则和系统行为，不在模块规模
- 结构越轻，越适合在真机上快速调试提醒问题

---

## 5. 总体架构设计

### 5.1 总体原则

首版采用“单模块 + 按职责分包”的结构，不做一上来就多模块化。

建议目录结构：

```text
com.neupan.parking_reminder
├── ui
│   ├── home
│   ├── history
│   ├── settings
│   ├── alert
│   └── components
├── domain
│   ├── model
│   ├── rule
│   ├── service
│   └── usecase
├── data
│   ├── db
│   ├── dao
│   ├── entity
│   ├── repository
│   └── mapper
├── alarm
│   ├── scheduler
│   ├── receiver
│   ├── notifier
│   └── resync
└── platform
    ├── permission
    ├── boot
    └── battery
```

### 5.2 各层职责

`ui`

- Compose 页面和弹窗
- ViewModel 状态管理
- 权限引导
- 用户交互

`domain`

- 费用计算
- 倒计时和下一提醒点计算
- 状态机转换
- 修改入库时间时的校验规则

`data`

- Room 数据表、DAO、Repository
- 当前停车状态持久化
- 已缴费覆盖周期持久化
- 历史记录持久化

`alarm`

- 注册提醒
- 取消提醒
- 接收提醒广播
- 发送提醒通知
- 重启后重建提醒

`platform`

- 通知权限检查
- 精确闹钟能力检查
- 小米/红米兼容引导
- 开机、改时区、改系统时间等系统事件处理

---

## 6. 业务规则落地

这一节的目标是把 PRD 里的自然语言，收敛成后面可以直接写成代码和单测的规则。

### 6.1 核心概念

`ParkingSession`

- 一次从入库到出库的停车过程
- 只要用户已经开始泊车且尚未出库，就存在一个活动中的停车会话

`CoverageWindow`

- 只有当一次出库时产生了费用 `> 0`，才会创建一个新的 12 小时覆盖周期
- 在该周期内再次入库，可以被上一次缴费覆盖

`ReminderNode`

- 一条未来需要提醒用户的时间点
- 用来驱动精确闹钟调度

### 6.2 时间区间约定

为了避免边界歧义，所有计费区间统一采用左闭右开：

- `[start, end)`

也就是：

- 起点包含
- 终点不包含

这样能把“刚好在 1 小时 / 12 小时 / 24 小时整点”时的费用变化写清楚。

### 6.3 普通停车会话的计费规则

当用户开始停车时，不存在有效覆盖周期：

- `[entryAt, entryAt + 1h)` => `0 元`
- `[entryAt + 1h, entryAt + 12h)` => `5 元`
- `[entryAt + 12h, entryAt + 24h)` => `10 元`
- `[entryAt + 24h, entryAt + 36h)` => `15 元`

通俗地说：

- 入库满 1 小时，立即进入 `5 元`
- 之后每满 12 小时，再加 `5 元`

对应提醒点：

- `entryAt + 50m` => 即将计费 `5 元`
- `entryAt + 11h50m` => 即将加费到 `10 元`
- `entryAt + 23h50m` => 即将加费到 `15 元`

### 6.4 覆盖周期内再次入库的计费规则

当用户再次入库时，如果当前时间仍在有效覆盖周期内：

- `[reEntryAt, coverageEndAt)` => `0 元`
- 到 `coverageEndAt` 时，立即进入下一轮计费阶段
- `[coverageEndAt, coverageEndAt + 12h)` => `5 元`
- `[coverageEndAt + 12h, coverageEndAt + 24h)` => `10 元`

这里有一个已经确认的关键规则：

- 覆盖周期结束后，不再额外赠送新的 1 小时免费时间
- 覆盖周期一结束，就直接进入下一轮计费窗口

对应提醒点：

- `coverageEndAt - 10m` => 即将进入新计费阶段 `5 元`
- `coverageEndAt + 11h50m` => 即将加费到 `10 元`
- `coverageEndAt + 23h50m` => 即将加费到 `15 元`

### 6.5 出库规则

当用户点击“已缴费出库”时：

- 当前停车会话结束
- 写入一条历史记录
- 如果本次费用 `> 0`，创建新的 `CoverageWindow`
- `CoverageWindow.startAt = checkoutAt`
- `CoverageWindow.endAt = checkoutAt + 12h`

如果本次费用为 `0`：

- 不创建新的覆盖周期

### 6.6 修改入库时间规则

当用户在停车过程中手动修改入库时间时：

- 立即更新当前停车会话
- 立即重算当前费用和当前状态
- 取消当前已注册的提醒
- 根据新时间重新计算并注册下一条提醒

建议校验：

- 新入库时间不能晚于当前时间
- 如果当前会话匹配了某个覆盖周期，则入库时间不能早于该覆盖周期所依赖的最近一次出库时间

---

## 7. 状态机设计

### 7.1 运行时状态

建议将 App 的运行时状态抽象为以下几类：

- `Idle`
- `ParkingFree`
- `ParkingCharged`
- `ParkingCovered`
- `PostCoverageCharged`

### 7.2 状态含义

`Idle`

- 当前没有活动中的停车会话

`ParkingFree`

- 当前正在停车
- 没有有效覆盖周期
- 当前费用为 `0 元`
- 下一次费用变化点是 `entryAt + 1h`

`ParkingCharged`

- 当前正在停车
- 没有有效覆盖周期
- 当前费用已经大于等于 `5 元`

`ParkingCovered`

- 当前正在停车
- 本次停车被已有覆盖周期覆盖
- 当前费用为 `0 元`

`PostCoverageCharged`

- 本次停车开始时处在覆盖周期内
- 但覆盖周期已经在停车过程中结束
- 当前费用已经进入新的计费阶段

### 7.3 状态转换

`Idle -> ParkingFree`

- 用户开始泊车，且当前没有有效覆盖周期

`Idle -> ParkingCovered`

- 用户开始泊车，且当前命中了有效覆盖周期

`ParkingFree -> ParkingCharged`

- 到达 `entryAt + 1h`

`ParkingCovered -> PostCoverageCharged`

- 到达 `coverageEndAt`

`ParkingCharged -> Idle`

- 用户出库

`ParkingCovered -> Idle`

- 用户在覆盖期内出库

`PostCoverageCharged -> Idle`

- 用户出库

这个状态机应该写成纯 Kotlin 逻辑，并优先做单元测试，不要让 Android API 直接混进规则层。

---

## 8. 提醒调度方案

### 8.1 只维护一条未来提醒

MVP 建议采用“永远只注册下一条提醒”的策略。

也就是：

- 当前时刻只保留一条未来的精确闹钟
- 这条闹钟对应最近的那个 `ReminderNode`

触发后流程如下：

1. 展示提醒
2. 从本地存储读取最新状态
3. 判断当前停车会话是否仍然有效
4. 重新计算下一条提醒点
5. 如果仍需要提醒，则再注册下一条

这样做的好处：

- 取消和重建逻辑非常简单
- 不容易出现一堆历史闹钟残留
- 对系统来说也更像一个正常的“下一次提醒”

### 8.2 什么时候需要重建提醒

以下时机都需要重新执行提醒同步逻辑：

- 用户开始泊车
- 用户修改入库时间
- 用户出库
- 某条提醒刚刚触发
- App 冷启动
- 设备重启
- App 更新后首次启动或收到包替换广播
- 时区变化
- 系统时间被手动修改
- 精确闹钟能力状态发生变化

### 8.3 闹钟携带的数据

每条闹钟建议至少携带以下信息：

- `parkingSessionId`
- `reminderType`
- `expectedTriggerAt`
- `targetFee`

但要注意：

- Receiver 收到广播后，必须重新从 Room 读取真实状态
- 不能把闹钟 extra 当成最终真相

这样才能避免：

- 用户修改过时间，但旧提醒还在
- 用户已经出库，但旧提醒误报

### 8.4 提醒展示方式

MVP 的提醒表现建议如下：

- 独立通知渠道 `parking_alarm`
- 高重要级
- 开启振动
- 配置提醒音
- 音频属性使用接近闹钟的类型
- 锁屏可见
- 通知分类设为 `alarm`

通知内容建议：

- 标题：停车缴费提醒
- 内容：即将增加到的费用 + 尽快缴费离场的动作建议
- 操作按钮：
  - 打开 App
  - 我知道了

后续增强项，不放在首轮必做里：

- 独立提醒页 `ReminderAlertActivity`
- 更强的全屏提醒
- 自定义响铃
- 未确认前重复提醒

这些增强功能要等红米 K50 真机验证稳定后再加，因为 OEM ROM 对强打断提醒的处理差异会比较大。

### 8.5 后台被杀时的提醒可靠性边界

提醒可靠性必须按不同系统状态分开看。

| 场景 | 预期结果 | 设计判断 |
|------|----------|----------|
| App 正常退到后台 | 提醒应生效 | `AlarmManager` 由系统调度，不依赖页面存活 |
| 系统因内存或省电回收 App 进程 | 提醒应生效 | 使用 `PendingIntent + BroadcastReceiver`，到点由系统重新拉起接收器 |
| 设备处于 Doze / 低功耗空闲 | `setAlarmClock()` 应生效 | 这是面向用户可见的关键精确提醒，系统会在必要时退出低功耗模式 |
| 用户从最近任务划掉 App | 需要真机验证 | AOSP 通常不等同于强停，但部分国产 ROM 可能会做更激进处理 |
| 用户在系统设置里点击“强行停止” | 不保证生效 | App 会进入 stopped state，系统会阻止或取消后续 `PendingIntent` |
| Android 15 stopped state | 不保证生效 | 系统会取消该 App 的所有 pending intents |
| 手机重启 | 原提醒会丢失，但可恢复 | 监听 `BOOT_COMPLETED` 后从 Room 重建下一条提醒 |
| 精确闹钟权限被撤销 | 不保证生效 | 系统会取消未来的精确闹钟，需要重新授权后重建 |
| 通知权限被关闭 | 闹钟可能触发，但用户感知不到 | App 必须在健康检查中明确提示 |

结论：

- 设计目标是保证“App 不在前台、进程被系统回收、设备休眠”时提醒仍能触发
- 不承诺覆盖“强行停止”或系统进入 stopped state 后的提醒
- 对 Redmi / HyperOS 的最近任务清理行为必须真机验证，不能只靠官方文档推断

### 8.6 提醒可靠性保障措施

为尽量保证提醒能够生效，首版实现必须遵守以下规则：

- 使用 `setAlarmClock()` 作为主调度方式
- 使用 `PendingIntent.getBroadcast()` 指向 manifest 声明的 `ParkingReminderReceiver`
- Receiver 触发后立即读取 Room 中的最新停车状态
- 每次开始泊车、修改入库时间、出库后都取消旧提醒并重建下一条
- App 每次冷启动时检查当前活动停车状态，并重建下一条提醒
- 设备重启后通过 `BOOT_COMPLETED` 重建下一条提醒
- App 更新后通过 `MY_PACKAGE_REPLACED` 或首次启动检查重建下一条提醒
- 精确闹钟权限状态变化后重新检查能力并重建提醒
- 通知发送失败或权限缺失时，在 App 首页展示明确的提醒风险状态

实现注意：

- 不使用只绑定当前进程生命周期的 `OnAlarmListener` 方案承载核心提醒
- 不把内存中的倒计时当成提醒来源，倒计时只服务 UI 展示
- 不用常驻前台服务作为 MVP 主方案，除非真机验证证明精确闹钟链路无法满足需求

---

## 9. 精确闹钟权限策略

### 9.1 当前阶段的推荐策略

考虑到这个项目当前是自用 APK，不以上架为目标，首版可以优先采用：

- `USE_EXACT_ALARM`

优点：

- 安装后通常不需要用户再走“特殊权限设置”流程
- 更接近目标体验
- 自用场景下接入成本最低

### 9.2 未来如果要上架

如果后续要上应用商店，这一策略必须重新评估。

可能的调整方向：

- 论证自己属于符合政策的提醒类应用
- 或切换到 `SCHEDULE_EXACT_ALARM` + 用户手动授权
- 或重新设计提醒强度与可靠性承诺

### 9.3 运行时健康检查

即便采用了上面的权限策略，App 里仍建议提供一个“提醒健康检查”区域，至少展示：

- 通知是否开启
- 精确闹钟能力是否可用
- 电池优化是否关闭
- 是否需要开启自启动
- 最近一次提醒是否成功注册
- 下一次提醒的计划触发时间
- 当前 App 是否需要用户重新打开以恢复提醒

关键原则：

- 不要在提醒能力不足时偷偷降级到 WorkManager，然后假装一切可靠
- 如果提醒链路不完整，要明确提示用户“当前提醒可靠性已下降”

---

## 10. 本地存储方案

### 10.1 主存储结论

业务主数据统一用 Room 存储。

不建议把核心业务状态放在 DataStore 里。

原因：

- 这个项目的状态已经是结构化数据，不只是简单配置项
- BroadcastReceiver 在后台恢复状态时，读表比读一坨键值更稳定
- 后续排查提醒问题时，Room 更容易观察和调试

### 10.2 建议的数据表

`active_parking_session`

- 用来保存当前活动中的停车会话

建议字段：

- `id`
- `entry_at`
- `matched_coverage_window_id`
- `created_at`
- `updated_at`

`coverage_window`

- 用来保存当前和历史的已缴费覆盖周期

建议字段：

- `id`
- `start_at`
- `end_at`
- `source_history_id`
- `is_active`
- `created_at`

`parking_history`

- 用来保存已结束的停车历史

建议字段：

- `id`
- `entry_at`
- `exit_at`
- `duration_minutes`
- `fee_yuan`
- `was_covered`
- `coverage_window_id`
- `created_at`

轻量偏好项如果后面确实需要，可以单独用 DataStore，例如：

- 是否完成兼容性引导
- 是否自定义提醒声音
- 某些说明弹窗是否已关闭

### 10.3 状态归属原则

每类数据只允许一个真实来源：

- Room 是业务真相
- ViewModel 只保存 UI 投影
- Alarm Receiver 只从 Repository 读状态，不依赖内存缓存

---

## 11. 领域服务与仓储设计

建议抽出以下核心服务：

`BillingCalculator`

- 计算当前费用
- 计算当前文案状态
- 计算下一次费用变化点

`CoverageMatcher`

- 判断新的入库时间是否落在有效覆盖周期内

`ReminderPlanner`

- 计算下一条提醒点
- 生成提醒文案上下文

`ParkingRepository`

- 开始泊车
- 修改入库时间
- 出库
- 查询当前状态
- 查询历史记录

`ReminderScheduler`

- 注册提醒
- 取消提醒
- 根据当前状态重建提醒

`ReminderNotifier`

- 负责真正发送通知

这个拆法对 MVP 已经足够，不需要一开始引入很重的 Clean Architecture 套路。

---

## 12. UI 与交互规划

### 12.1 页面规划

`HomeScreen`

- 当前停车状态卡片
- 当前费用
- 距离下一次变化的倒计时
- 开始泊车按钮
- 已缴费出库按钮
- 修改入库时间入口
- 提醒健康检查入口

`HistoryScreen`

- 展示最近停车记录
- 最多保留 50 条
- 支持清空历史

`SettingsScreen`

- 通知权限状态
- 精确闹钟状态
- 电池优化说明
- 小米/红米兼容引导

`ReminderAlertActivity`

- 作为后续增强项
- 从提醒通知点击进入，或在更强提醒模式下直接展示

### 12.2 UI 优先级

MVP 的 UI 优先级顺序应该是：

1. 状态正确
2. 提醒可靠
3. 交互够快
4. 视觉再优化

也就是说，前期不要把主要精力花在动画和高级视觉上，先把真机提醒链路跑通。

---

## 13. 需要新增的 Android 组件

### 13.1 Activity

- `MainActivity`
- 可选 `ReminderAlertActivity`

### 13.2 BroadcastReceiver

- `ParkingReminderReceiver`
- `BootCompletedReceiver`
- `TimeChangedReceiver`
- `TimezoneChangedReceiver`
- `PackageReplacedReceiver`
- 如后续需要，再补精确闹钟权限状态相关的 Receiver

### 13.3 Manifest 侧需要补充的内容

预计需要补充：

- 通知权限
- 精确闹钟权限
- 开机广播权限
- 对应 Receiver 声明
- App 更新后重新同步提醒的广播声明

可能涉及的权限：

- `android.permission.POST_NOTIFICATIONS`
- `android.permission.RECEIVE_BOOT_COMPLETED`
- 精确闹钟相关权限

---

## 14. 小米 / 红米兼容策略

这个项目必须默认承认一件事：

- OEM 后台限制是真实存在的

### 14.1 产品侧策略

建议 App 内置一个简单的兼容性检查清单，尤其面向 Redmi K50：

- 通知是否开启
- 自启动是否开启
- 电池优化是否关闭
- 提醒渠道是否有声音
- App 是否已在最近任务中加锁
- 用户是否了解“强行停止”会导致提醒失效

### 14.2 工程侧策略

不建议为了兼容性去做下面这些“看起来很猛、实际上副作用很大”的方案：

- 常驻前台服务
- 高频后台轮询
- 假装定时器常驻运行

原因：

- 耗电高
- 体验差
- 对这种精确提醒问题也不一定更可靠

### 14.3 真机验收策略

以下场景必须在红米 K50 真机上验证：

- 熄屏过夜
- App 任务被上滑杀掉
- App 在最近任务中加锁后熄屏
- App 未在最近任务中加锁时被清理
- 开启省电模式
- 手机重启后恢复
- App 更新后恢复
- 修改入库时间后的提醒重建
- 覆盖周期结束前 10 分钟提醒是否正常
- 系统设置中“强行停止”后的预期失效提示是否清晰

---

## 15. 测试策略

### 15.1 单元测试

以下逻辑必须重点做单元测试：

- 普通停车的费用计算
- 覆盖周期停车的费用计算
- 下一提醒点计算
- 状态机转换
- 出库后覆盖周期创建逻辑
- 修改入库时间后的状态重算

### 15.2 仪器测试

只覆盖关键路径即可：

- 权限引导
- 开始泊车
- 出库
- 历史记录渲染

### 15.3 真机手测

目标设备上至少要验证：

- 50 分钟提醒
- 11 小时 50 分钟提醒
- 覆盖期结束前 10 分钟提醒
- 锁屏时的声音和振动
- 重启后的提醒恢复
- App 进程被系统回收后的提醒
- 最近任务划掉后的提醒
- 最近任务加锁后的提醒
- 系统“强行停止”后的预期失效行为
- 精确闹钟权限关闭后的风险提示
- 通知权限关闭后的风险提示

为了缩短开发期测试成本，后面可以加一个 debug-only 的时间加速能力，但正式规则层依然必须使用真实时间戳。

### 15.4 提醒可靠性验收标准

首版只有在以下条件满足后，才能认为提醒链路通过 MVP 验收：

- App 正常退后台后，到点能提醒
- 熄屏后，到点能提醒
- App 进程被系统回收后，到点能提醒
- App 安装后至少打开过一次，手机重启后能通过 `BOOT_COMPLETED` 重建提醒并到点提醒
- 通知权限关闭时，首页能明确提示“提醒无法正常展示”
- 精确闹钟能力不可用时，首页能明确提示“提醒不可靠”
- 系统强行停止后，文档和 App 内说明都明确提示“需要重新打开 App 才能恢复提醒”

---

## 16. MVP 范围

### 16.1 本次要做的

- 一键开始泊车
- 实时费用计算
- 倒计时展示
- 精确提醒调度
- 12 小时覆盖周期追踪
- 修改入库时间
- 本地停车历史
- 重启恢复提醒
- 小米/红米兼容引导

### 16.2 本次先不做的

- 云同步
- iOS
- 多车辆
- 多城市规则自定义
- 账号体系
- 日历导入兜底

---

## 17. 建议开发顺序

推荐按下面顺序推进：

1. 先写纯规则层和单元测试
2. 再接 Room 和 Repository
3. 先做开始泊车 / 出库 / 修改入库时间的主流程
4. 再接精确闹钟调度和 Receiver
5. 再接高优先级通知渠道
6. 再处理重启恢复、改时区、改系统时间
7. 最后补设置页和兼容性引导
8. 在红米 K50 上做整轮真机验证
9. 验证通过后再优化 UI 和提醒强度

这样可以避免前面先花很多时间做界面，最后却发现真正难点卡在提醒可靠性上。

---

## 18. 主要风险与应对

### 风险 1：不同设备对精确闹钟的表现不一致

应对：

- 尽早在真机验证
- 提醒调度逻辑保持简单
- 永远只维护一条下一提醒

### 风险 2：用户修改时间或出库后出现陈旧提醒

应对：

- 每次状态变化都取消并重建提醒
- Receiver 到点后重新从 Room 读取状态

### 风险 3：边界时间点处理不一致，导致费用错误

应对：

- 明确统一使用左闭右开区间
- 把 1h / 12h / 24h / coverageEndAt 等边界点全部写成单测

### 风险 4：小米系电池策略导致提醒不稳定

应对：

- App 内提供清晰兼容性引导
- 所有关键场景都在目标真机上验证

### 风险 5：未来上架时精确闹钟权限策略需要调整

应对：

- 调度能力抽象成独立层
- 不把权限策略写死在业务逻辑里

### 风险 6：用户或系统让 App 进入 stopped state

应对：

- App 内明确说明“强行停止后提醒会失效”
- 每次用户重新打开 App 时自动检查并恢复下一条提醒
- 在 Redmi / HyperOS 上单独验证“最近任务划掉”和“系统强行停止”的行为差异
- 把 stopped state 视为无法完全规避的系统级边界，而不是实现缺陷

---

## 19. 下一步建议

在正式开写代码前，建议紧接着补一份“开发蓝图”文档，内容包括：

- package skeleton
- Room 实体定义
- Repository 接口
- 规则层接口
- 提醒调度接口
- 首批页面的状态模型

这样做的好处是：

- 代码实现时不会一边写一边想结构
- 我们可以先把关键类名、职责和边界定死，再开始开发

---

## 20. 参考资料

- [PRD](./PRD.md)
- [Android Developers - Schedule alarms](https://developer.android.com/develop/background-work/services/alarms/schedule)
- [Android Developers - Schedule exact alarms are denied by default](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)
- [Android Developers - Notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission)
- [Android Developers - WorkManager guidance](https://developer.android.com/develop/background-work/background-tasks/persistent)
- [Android Developers - Android 15 stopped state changes](https://developer.android.com/about/versions/15/behavior-changes-all#enhanced-stop-states)
