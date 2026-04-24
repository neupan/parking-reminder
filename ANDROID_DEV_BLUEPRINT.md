# 停车提醒 Android 开发蓝图

## 1. 文档目的

本文档是 [Android 技术方案](./ANDROID_TECH_PLAN.md) 的下一层落地蓝图。

它用于在正式编码前先确定：

- package skeleton
- Room 实体和 DAO 形状
- Repository 接口
- 规则层类名和职责
- 提醒调度接口
- 首批页面状态模型

后续实现时优先遵循本文档命名和边界。若实现中发现更好的局部调整，应先更新本文档，再改代码。

---

## 2. 实现原则

首版按以下原则推进：

- 单模块 `app`
- Kotlin + Compose
- Room 作为业务数据真相
- 规则层保持纯 Kotlin，不依赖 Android Framework
- 提醒调度封装在 `alarm` 包内，不让 UI 或 Repository 直接操作 `AlarmManager`
- App 只维护下一条未来提醒
- 所有时间在数据库中存 `epochMillis`
- domain 层使用 `Instant` / `Duration` 语义
- UI 层统一按设备本地时区格式化展示

时间类型建议：

```kotlin
typealias EpochMillis = Long
```

说明：

- Room entity 使用 `Long` 存时间，避免 TypeConverter 过早复杂化
- domain mapper 负责在 `Long` 和 `Instant` 之间转换
- 所有费用用 `Int` 表示元，例如 `5`、`10`、`15`

---

## 3. Package Skeleton

目标包结构：

```text
app/src/main/java/com/neupan/parking_reminder
├── MainActivity.kt
├── ParkingReminderApp.kt
├── alarm
│   ├── ParkingReminderReceiver.kt
│   ├── BootCompletedReceiver.kt
│   ├── TimeChangedReceiver.kt
│   ├── TimezoneChangedReceiver.kt
│   ├── PackageReplacedReceiver.kt
│   ├── ReminderAlarmScheduler.kt
│   ├── ReminderNotifier.kt
│   ├── ReminderResyncService.kt
│   └── model
│       ├── ReminderAlarmPayload.kt
│       ├── ReminderScheduleResult.kt
│       └── ReminderSyncReason.kt
├── data
│   ├── AppDatabase.kt
│   ├── dao
│   │   ├── ActiveParkingSessionDao.kt
│   │   ├── CoverageWindowDao.kt
│   │   ├── ParkingHistoryDao.kt
│   │   └── ReminderScheduleStateDao.kt
│   ├── entity
│   │   ├── ActiveParkingSessionEntity.kt
│   │   ├── CoverageWindowEntity.kt
│   │   ├── ParkingHistoryEntity.kt
│   │   └── ReminderScheduleStateEntity.kt
│   ├── mapper
│   │   ├── ParkingEntityMappers.kt
│   │   └── ReminderEntityMappers.kt
│   └── repository
│       ├── RoomParkingRepository.kt
│       └── RoomReminderStateRepository.kt
├── domain
│   ├── model
│   │   ├── BillingQuote.kt
│   │   ├── CoverageWindow.kt
│   │   ├── ParkingHistory.kt
│   │   ├── ParkingSession.kt
│   │   ├── ParkingSnapshot.kt
│   │   ├── ParkingStatus.kt
│   │   ├── ReminderPlan.kt
│   │   └── ReminderType.kt
│   ├── repository
│   │   ├── ParkingRepository.kt
│   │   └── ReminderStateRepository.kt
│   ├── rule
│   │   ├── BillingCalculator.kt
│   │   ├── CoverageMatcher.kt
│   │   ├── ParkingStateResolver.kt
│   │   └── ReminderPlanner.kt
│   ├── service
│   │   ├── ParkingCommandService.kt
│   │   └── ReminderHealthService.kt
│   └── time
│       ├── AppClock.kt
│       └── SystemAppClock.kt
├── platform
│   ├── PermissionChecker.kt
│   ├── BatteryOptimizationChecker.kt
│   └── StartupCapabilityChecker.kt
└── ui
    ├── ParkingReminderRoot.kt
    ├── home
    │   ├── HomeScreen.kt
    │   ├── HomeUiState.kt
    │   └── HomeViewModel.kt
    ├── history
    │   ├── HistoryScreen.kt
    │   ├── HistoryUiState.kt
    │   └── HistoryViewModel.kt
    ├── settings
    │   ├── SettingsScreen.kt
    │   ├── SettingsUiState.kt
    │   └── SettingsViewModel.kt
    └── alert
        ├── ReminderAlertActivity.kt
        └── ReminderAlertUiState.kt
```

MVP 可以先不实现 `ReminderAlertActivity`，但包和状态模型可以先预留。

---

## 4. Room Schema

### 4.1 数据库定义

```kotlin
@Database(
    entities = [
        ActiveParkingSessionEntity::class,
        CoverageWindowEntity::class,
        ParkingHistoryEntity::class,
        ReminderScheduleStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun activeParkingSessionDao(): ActiveParkingSessionDao
    abstract fun coverageWindowDao(): CoverageWindowDao
    abstract fun parkingHistoryDao(): ParkingHistoryDao
    abstract fun reminderScheduleStateDao(): ReminderScheduleStateDao
}
```

数据库命名：

```kotlin
const val DATABASE_NAME = "parking_reminder.db"
```

### 4.2 `ActiveParkingSessionEntity`

用途：

- 保存当前唯一的活动停车会话
- 表内最多保留一条记录
- Repository 通过事务保证插入新会话前清空旧会话

```kotlin
@Entity(tableName = "active_parking_session")
data class ActiveParkingSessionEntity(
    @PrimaryKey val id: String,
    val entryAtEpochMillis: Long,
    val matchedCoverageWindowId: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
```

字段说明：

| 字段 | 含义 |
|------|------|
| `id` | 当前停车会话 id，建议使用 UUID 字符串 |
| `entryAtEpochMillis` | 入库时间 |
| `matchedCoverageWindowId` | 本次入库命中的覆盖周期，普通停车为 `null` |
| `createdAtEpochMillis` | 会话创建时间 |
| `updatedAtEpochMillis` | 最近更新时间 |

### 4.3 `CoverageWindowEntity`

用途：

- 保存一次已缴费出库后产生的 12 小时覆盖周期
- 覆盖周期结束后仍建议保留历史，方便事后补录入库时间时正确匹配

```kotlin
@Entity(
    tableName = "coverage_window",
    indices = [
        Index(value = ["startAtEpochMillis", "endAtEpochMillis"]),
        Index(value = ["isActive"]),
        Index(value = ["sourceHistoryId"]),
    ],
)
data class CoverageWindowEntity(
    @PrimaryKey val id: String,
    val startAtEpochMillis: Long,
    val endAtEpochMillis: Long,
    val sourceHistoryId: String,
    val isActive: Boolean,
    val createdAtEpochMillis: Long,
)
```

字段说明：

| 字段 | 含义 |
|------|------|
| `id` | 覆盖周期 id |
| `startAtEpochMillis` | 覆盖周期开始时间，等于缴费出库时间 |
| `endAtEpochMillis` | 覆盖周期结束时间，等于出库时间 + 12h |
| `sourceHistoryId` | 产生该覆盖周期的历史记录 id |
| `isActive` | 是否仍作为有效历史规则使用，不因 `endAt` 早于当前时间而自动置为 `false` |
| `createdAtEpochMillis` | 记录创建时间 |

匹配规则：

```text
isActive = true
AND startAtEpochMillis <= entryAt
AND entryAt < endAtEpochMillis
```

注意：

- 不要仅因为 `endAtEpochMillis <= now` 就把 `isActive` 改成 `false`
- 新入库是否被覆盖，只取决于 `entryAt` 是否落在某个有效覆盖周期内
- 这样用户事后补录入库时间时，仍可以正确识别“当时入库发生在覆盖周期内”

### 4.4 `ParkingHistoryEntity`

用途：

- 保存已完成的停车记录
- MVP 最多保留最近 50 条

```kotlin
@Entity(
    tableName = "parking_history",
    indices = [
        Index(value = ["exitAtEpochMillis"]),
        Index(value = ["coverageWindowId"]),
    ],
)
data class ParkingHistoryEntity(
    @PrimaryKey val id: String,
    val entryAtEpochMillis: Long,
    val exitAtEpochMillis: Long,
    val durationMinutes: Long,
    val feeYuan: Int,
    val wasCovered: Boolean,
    val coverageWindowId: String?,
    val createdAtEpochMillis: Long,
)
```

字段说明：

| 字段 | 含义 |
|------|------|
| `id` | 历史记录 id |
| `entryAtEpochMillis` | 入库时间 |
| `exitAtEpochMillis` | 出库时间 |
| `durationMinutes` | 停车时长，向下取整到分钟 |
| `feeYuan` | 本次停车最终费用 |
| `wasCovered` | 本次是否命中过覆盖周期 |
| `coverageWindowId` | 命中的覆盖周期 id，没有则为 `null` |
| `createdAtEpochMillis` | 历史记录创建时间 |

### 4.5 `ReminderScheduleStateEntity`

用途：

- 保存当前系统中“理论上已注册”的下一条提醒
- 支撑首页和设置页的提醒健康检查
- 方便排查真机上提醒是否注册成功

```kotlin
@Entity(tableName = "reminder_schedule_state")
data class ReminderScheduleStateEntity(
    @PrimaryKey val id: String = "current",
    val sessionId: String?,
    val reminderType: String?,
    val triggerAtEpochMillis: Long?,
    val targetFeeYuan: Int?,
    val scheduledAtEpochMillis: Long?,
    val status: String,
    val failureReason: String?,
    val updatedAtEpochMillis: Long,
)
```

`status` 建议值：

```text
NONE
SCHEDULED
FIRED
CANCELED
FAILED
```

注意：

- 这张表不是系统闹钟本身，只是 App 自己记录的调度状态
- 真正的提醒仍以 `AlarmManager` 中注册的 `PendingIntent` 为准
- 每次注册、取消、触发失败都要更新这张表

---

## 5. DAO Blueprint

### 5.1 `ActiveParkingSessionDao`

```kotlin
@Dao
interface ActiveParkingSessionDao {
    @Query("SELECT * FROM active_parking_session LIMIT 1")
    fun observeActiveSession(): Flow<ActiveParkingSessionEntity?>

    @Query("SELECT * FROM active_parking_session LIMIT 1")
    suspend fun getActiveSession(): ActiveParkingSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ActiveParkingSessionEntity)

    @Query("DELETE FROM active_parking_session")
    suspend fun clear()
}
```

Repository 插入新会话时必须使用事务：

```kotlin
clear()
insert(newSession)
```

### 5.2 `CoverageWindowDao`

```kotlin
@Dao
interface CoverageWindowDao {
    @Query(
        """
        SELECT * FROM coverage_window
        WHERE isActive = 1
          AND startAtEpochMillis <= :atEpochMillis
          AND :atEpochMillis < endAtEpochMillis
        ORDER BY endAtEpochMillis DESC
        LIMIT 1
        """
    )
    suspend fun findCoveringWindow(atEpochMillis: Long): CoverageWindowEntity?

    @Query("SELECT * FROM coverage_window WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CoverageWindowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(window: CoverageWindowEntity)
}
```

### 5.3 `ParkingHistoryDao`

```kotlin
@Dao
interface ParkingHistoryDao {
    @Query("SELECT * FROM parking_history ORDER BY exitAtEpochMillis DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<ParkingHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: ParkingHistoryEntity)

    @Query("DELETE FROM parking_history")
    suspend fun clearAll()

    @Query(
        """
        DELETE FROM parking_history
        WHERE id NOT IN (
            SELECT id FROM parking_history
            ORDER BY exitAtEpochMillis DESC
            LIMIT :keepLatest
        )
        """
    )
    suspend fun pruneToLatest(keepLatest: Int)
}
```

### 5.4 `ReminderScheduleStateDao`

```kotlin
@Dao
interface ReminderScheduleStateDao {
    @Query("SELECT * FROM reminder_schedule_state WHERE id = 'current' LIMIT 1")
    fun observeCurrent(): Flow<ReminderScheduleStateEntity?>

    @Query("SELECT * FROM reminder_schedule_state WHERE id = 'current' LIMIT 1")
    suspend fun getCurrent(): ReminderScheduleStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: ReminderScheduleStateEntity)
}
```

---

## 6. Domain Models

### 6.1 `ParkingSession`

```kotlin
data class ParkingSession(
    val id: String,
    val entryAt: Instant,
    val matchedCoverageWindowId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

### 6.2 `CoverageWindow`

```kotlin
data class CoverageWindow(
    val id: String,
    val startAt: Instant,
    val endAt: Instant,
    val sourceHistoryId: String,
    val isActive: Boolean,
    val createdAt: Instant,
)
```

### 6.3 `ParkingHistory`

```kotlin
data class ParkingHistory(
    val id: String,
    val entryAt: Instant,
    val exitAt: Instant,
    val duration: Duration,
    val feeYuan: Int,
    val wasCovered: Boolean,
    val coverageWindowId: String?,
    val createdAt: Instant,
)
```

### 6.4 `ParkingStatus`

```kotlin
sealed interface ParkingStatus {
    data object Idle : ParkingStatus

    data class ParkingFree(
        val freeEndsAt: Instant,
    ) : ParkingStatus

    data class ParkingCharged(
        val currentFeeYuan: Int,
        val nextChargeAt: Instant,
        val nextFeeYuan: Int,
    ) : ParkingStatus

    data class ParkingCovered(
        val coverageWindow: CoverageWindow,
    ) : ParkingStatus

    data class PostCoverageCharged(
        val coverageWindow: CoverageWindow,
        val currentFeeYuan: Int,
        val nextChargeAt: Instant,
        val nextFeeYuan: Int,
    ) : ParkingStatus
}
```

### 6.5 `BillingQuote`

```kotlin
data class BillingQuote(
    val status: ParkingStatus,
    val currentFeeYuan: Int,
    val nextChargeAt: Instant?,
    val nextFeeYuan: Int?,
    val countdownTargetLabel: CountdownTargetLabel,
)

enum class CountdownTargetLabel {
    FREE_ENDS,
    NEXT_FEE_INCREASE,
    COVERAGE_ENDS,
}
```

### 6.6 `ReminderType` and `ReminderPlan`

```kotlin
enum class ReminderType {
    FREE_ENDING,
    FEE_INCREASING,
    COVERAGE_ENDING,
}

data class ReminderPlan(
    val sessionId: String,
    val reminderType: ReminderType,
    val triggerAt: Instant,
    val targetFeeYuan: Int,
)
```

### 6.7 `ParkingSnapshot`

用途：

- 给 UI 和提醒逻辑使用的一次性完整状态
- 由 `ParkingStateResolver` 聚合 session、coverage、billing quote 得到

```kotlin
data class ParkingSnapshot(
    val now: Instant,
    val activeSession: ParkingSession?,
    val matchedCoverageWindow: CoverageWindow?,
    val billingQuote: BillingQuote?,
    val nextReminderPlan: ReminderPlan?,
)
```

---

## 7. Rule Layer

规则层全部放在 `domain.rule`，并且必须可以在 JVM 单元测试中直接运行。

### 7.1 `BillingCalculator`

职责：

- 根据入库时间、当前时间、命中的覆盖周期计算当前费用
- 返回当前状态、下一次费用变化时间、下一档费用

接口：

```kotlin
class BillingCalculator {
    fun calculate(
        session: ParkingSession,
        matchedCoverageWindow: CoverageWindow?,
        now: Instant,
    ): BillingQuote
}
```

核心规则：

- 普通停车：`entryAt + 1h` 开始 `5 元`
- 普通停车：之后每 `12h` 加 `5 元`
- 覆盖期停车：`entryAt <= now < coverageEndAt` 为 `0 元`
- 覆盖期结束后：从 `coverageEndAt` 立即进入 `5 元`，不再额外给 1 小时免费期

### 7.2 `CoverageMatcher`

职责：

- 判断某个入库时间是否命中覆盖周期
- 只处理时间匹配，不负责查询数据库

接口：

```kotlin
class CoverageMatcher {
    fun isCovered(entryAt: Instant, window: CoverageWindow): Boolean
}
```

匹配规则：

```text
window.startAt <= entryAt && entryAt < window.endAt && window.isActive
```

### 7.3 `ReminderPlanner`

职责：

- 根据当前停车状态计算下一条提醒
- 如果已经没有未来提醒，返回 `null`

接口：

```kotlin
class ReminderPlanner {
    fun planNextReminder(
        session: ParkingSession,
        quote: BillingQuote,
        now: Instant,
    ): ReminderPlan?
}
```

提醒规则：

- 普通免费期：`entryAt + 50m` 提醒即将进入 `5 元`
- 普通计费期：每个 12h 节点前 10 分钟提醒
- 覆盖期：`coverageEndAt - 10m` 提醒即将进入新 `5 元`
- 覆盖期结束后：按 `coverageEndAt + 12h * n - 10m` 提醒下一档费用
- 如果计算出的提醒时间已经过去，跳到下一条未来提醒

### 7.4 `ParkingStateResolver`

职责：

- 聚合当前停车会话、覆盖周期、费用计算和提醒计划
- 输出 `ParkingSnapshot`

接口：

```kotlin
class ParkingStateResolver(
    private val billingCalculator: BillingCalculator,
    private val reminderPlanner: ReminderPlanner,
) {
    fun resolve(
        now: Instant,
        activeSession: ParkingSession?,
        matchedCoverageWindow: CoverageWindow?,
    ): ParkingSnapshot
}
```

### 7.5 `AppClock`

职责：

- 隔离系统时间，方便单测和 debug 时间加速

接口：

```kotlin
interface AppClock {
    fun now(): Instant
}

class SystemAppClock : AppClock {
    override fun now(): Instant = Instant.now()
}
```

---

## 8. Repository Interfaces

Repository 接口放在 `domain.repository`，Room 实现放在 `data.repository`。

### 8.1 `ParkingRepository`

```kotlin
interface ParkingRepository {
    fun observeActiveSession(): Flow<ParkingSession?>

    fun observeHistory(limit: Int = 50): Flow<List<ParkingHistory>>

    suspend fun getActiveSession(): ParkingSession?

    suspend fun findCoveringWindow(entryAt: Instant): CoverageWindow?

    suspend fun getCoverageWindow(id: String): CoverageWindow?

    suspend fun startParking(
        entryAt: Instant,
        now: Instant,
    ): StartParkingResult

    suspend fun updateEntryTime(
        entryAt: Instant,
        now: Instant,
    ): UpdateEntryTimeResult

    suspend fun checkout(
        exitAt: Instant,
        now: Instant,
    ): CheckoutResult

    suspend fun clearHistory()

    suspend fun pruneHistory(keepLatest: Int = 50)
}
```

结果模型：

```kotlin
data class StartParkingResult(
    val session: ParkingSession,
    val matchedCoverageWindow: CoverageWindow?,
)

data class UpdateEntryTimeResult(
    val session: ParkingSession,
    val matchedCoverageWindow: CoverageWindow?,
)

data class CheckoutResult(
    val history: ParkingHistory,
    val createdCoverageWindow: CoverageWindow?,
)
```

Repository 事务要求：

- `startParking()` 必须清空旧 active session 后插入新 session
- `updateEntryTime()` 必须同步更新 `matchedCoverageWindowId`
- `checkout()` 必须在同一个事务中写历史、按需写覆盖周期、清空 active session、裁剪历史数量

### 8.2 `ReminderStateRepository`

```kotlin
interface ReminderStateRepository {
    fun observeScheduleState(): Flow<ReminderScheduleState?>

    suspend fun getScheduleState(): ReminderScheduleState?

    suspend fun markScheduled(plan: ReminderPlan, scheduledAt: Instant)

    suspend fun markFired(plan: ReminderPlan, firedAt: Instant)

    suspend fun markCanceled(canceledAt: Instant)

    suspend fun markFailed(
        plan: ReminderPlan?,
        failedAt: Instant,
        reason: String,
    )
}
```

领域模型：

```kotlin
data class ReminderScheduleState(
    val sessionId: String?,
    val reminderType: ReminderType?,
    val triggerAt: Instant?,
    val targetFeeYuan: Int?,
    val scheduledAt: Instant?,
    val status: ReminderScheduleStatus,
    val failureReason: String?,
    val updatedAt: Instant,
)

enum class ReminderScheduleStatus {
    NONE,
    SCHEDULED,
    FIRED,
    CANCELED,
    FAILED,
}
```

---

## 9. Application Services

### 9.1 `ParkingCommandService`

职责：

- 承接 UI 发来的停车命令
- 调用 Repository 修改业务状态
- 每次业务状态变化后触发提醒重建

接口：

```kotlin
class ParkingCommandService(
    private val parkingRepository: ParkingRepository,
    private val reminderResyncService: ReminderResyncService,
    private val clock: AppClock,
) {
    suspend fun startParking(entryAt: Instant = clock.now())

    suspend fun updateEntryTime(entryAt: Instant)

    suspend fun checkout()

    suspend fun clearHistory()
}
```

调用规则：

- `startParking()` 成功后调用 `reminderResyncService.resync(USER_STARTED_PARKING)`
- `updateEntryTime()` 成功后调用 `reminderResyncService.resync(USER_EDITED_ENTRY_TIME)`
- `checkout()` 成功后调用 `reminderResyncService.resync(USER_CHECKED_OUT)`

### 9.2 `ReminderHealthService`

职责：

- 聚合权限、系统能力、最近调度状态
- 输出首页和设置页需要展示的提醒健康状态

接口：

```kotlin
class ReminderHealthService(
    private val permissionChecker: PermissionChecker,
    private val batteryOptimizationChecker: BatteryOptimizationChecker,
    private val startupCapabilityChecker: StartupCapabilityChecker,
    private val reminderStateRepository: ReminderStateRepository,
) {
    fun observeHealth(): Flow<ReminderHealth>
}
```

模型：

```kotlin
data class ReminderHealth(
    val notificationsEnabled: Boolean,
    val exactAlarmAvailable: Boolean,
    val batteryOptimizationIgnored: Boolean?,
    val startupHintNeeded: Boolean,
    val scheduleState: ReminderScheduleState?,
    val riskLevel: ReminderRiskLevel,
    val message: String?,
)

enum class ReminderRiskLevel {
    OK,
    WARNING,
    BLOCKED,
}
```

---

## 10. Reminder Scheduling Interfaces

提醒接口放在 `alarm` 包内。domain 层只知道 `ReminderPlan`，不直接接触 Android API。

### 10.1 `ReminderScheduler`

```kotlin
interface ReminderScheduler {
    suspend fun schedule(plan: ReminderPlan): ReminderScheduleResult

    suspend fun cancelCurrent(): ReminderScheduleResult

    fun canScheduleExactAlarms(): Boolean
}
```

结果模型：

```kotlin
sealed interface ReminderScheduleResult {
    data class Success(val plan: ReminderPlan?) : ReminderScheduleResult
    data class Failure(val reason: String) : ReminderScheduleResult
}
```

实现类：

```kotlin
class ReminderAlarmScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager,
) : ReminderScheduler
```

实现要求：

- 主实现使用 `setAlarmClock()`
- `PendingIntent` 必须使用 `getBroadcast()`
- `requestCode` 使用固定值，确保新提醒替换旧提醒
- `PendingIntent` extras 携带 `ReminderAlarmPayload`
- 只负责系统闹钟注册和取消，不直接写 Room
- 注册结果由 `ReminderResyncService` 写入 `ReminderStateRepository`

### 10.2 `ReminderAlarmPayload`

```kotlin
data class ReminderAlarmPayload(
    val sessionId: String,
    val reminderType: ReminderType,
    val expectedTriggerAtEpochMillis: Long,
    val targetFeeYuan: Int,
)
```

注意：

- Payload 只用于识别和日志，不作为最终业务真相
- Receiver 到点后必须重读 Room

### 10.3 `ReminderResyncService`

职责：

- 根据当前持久化业务状态，取消旧提醒并注册下一条提醒
- 被 UI 命令、Receiver、开机广播、时间变化广播调用

```kotlin
class ReminderResyncService(
    private val parkingRepository: ParkingRepository,
    private val reminderStateRepository: ReminderStateRepository,
    private val parkingStateResolver: ParkingStateResolver,
    private val reminderScheduler: ReminderScheduler,
    private val reminderNotifier: ReminderNotifier,
    private val clock: AppClock,
) {
    suspend fun resync(reason: ReminderSyncReason)

    suspend fun handleAlarm(payload: ReminderAlarmPayload)
}
```

`ReminderSyncReason`：

```kotlin
enum class ReminderSyncReason {
    USER_STARTED_PARKING,
    USER_EDITED_ENTRY_TIME,
    USER_CHECKED_OUT,
    ALARM_FIRED,
    APP_COLD_START,
    BOOT_COMPLETED,
    PACKAGE_REPLACED,
    TIME_CHANGED,
    TIMEZONE_CHANGED,
    EXACT_ALARM_PERMISSION_CHANGED,
}
```

`resync()` 流程：

1. 读取 active session
2. 若没有 active session，取消当前提醒并写入 `markCanceled()`
3. 若有 active session，读取 matched coverage window
4. 用 `ParkingStateResolver` 计算 snapshot
5. 若无下一条提醒，取消当前提醒并写入 `markCanceled()`
6. 若有下一条提醒，调用 `ReminderScheduler.schedule(plan)`
7. 根据调度结果写入 `ReminderStateRepository.markScheduled()` 或 `markFailed()`

`handleAlarm()` 流程：

1. 标记当前 payload 对应提醒已触发
2. 读取最新 active session
3. 若 session 不存在，取消当前提醒并结束
4. 若 session id 与 payload 不一致，视为陈旧提醒，不展示通知，只 resync
5. 若 session 匹配，重新计算当前 snapshot
6. 展示通知
7. 调用 `resync(ALARM_FIRED)` 安排下一条提醒

### 10.4 `ReminderNotifier`

```kotlin
interface ReminderNotifier {
    suspend fun showReminder(
        plan: ReminderPlan,
        snapshot: ParkingSnapshot,
    )
}
```

实现类：

```kotlin
class AndroidReminderNotifier(
    private val context: Context,
    private val notificationManager: NotificationManagerCompat,
) : ReminderNotifier
```

通知渠道：

```text
channelId = parking_alarm
importance = high
category = alarm
lockScreenVisibility = public
```

---

## 11. Platform Interfaces

### 11.1 `PermissionChecker`

```kotlin
interface PermissionChecker {
    fun areNotificationsEnabled(): Boolean

    fun canScheduleExactAlarms(): Boolean
}
```

### 11.2 `BatteryOptimizationChecker`

```kotlin
interface BatteryOptimizationChecker {
    fun isIgnoringBatteryOptimizations(): Boolean?
}
```

`Boolean?` 含义：

- `true`：已忽略电池优化
- `false`：未忽略
- `null`：当前系统无法可靠判断

### 11.3 `StartupCapabilityChecker`

```kotlin
interface StartupCapabilityChecker {
    fun shouldShowOemStartupHint(): Boolean
}
```

说明：

- Android 标准 API 无法可靠判断小米自启动是否开启
- 该接口首版可以基于设备品牌和用户是否关闭提示来决定是否展示说明

---

## 12. UI State Models

UI state 放在各自 feature 包内，只包含展示所需数据，不直接暴露 Room entity。

### 12.1 `HomeUiState`

```kotlin
data class HomeUiState(
    val isLoading: Boolean = true,
    val mode: HomeMode = HomeMode.Idle,
    val currentFeeYuan: Int = 0,
    val primaryText: String = "",
    val secondaryText: String = "",
    val entryAtText: String? = null,
    val nextChangeAtText: String? = null,
    val countdownText: String? = null,
    val progressFraction: Float = 0f,
    val urgency: UrgencyLevel = UrgencyLevel.SAFE,
    val reminderHealth: ReminderHealthUiState = ReminderHealthUiState(),
    val canStartParking: Boolean = true,
    val canCheckout: Boolean = false,
    val canEditEntryTime: Boolean = false,
)

enum class HomeMode {
    Idle,
    ParkingFree,
    ParkingCharged,
    ParkingCovered,
    PostCoverageCharged,
}

enum class UrgencyLevel {
    SAFE,
    WARNING,
    URGENT,
}
```

`progressFraction` 规则：

- 范围 `0f..1f`
- 表示当前倒计时周期已消耗比例
- 无活动停车时为 `0f`

### 12.2 `ReminderHealthUiState`

```kotlin
data class ReminderHealthUiState(
    val riskLevel: ReminderRiskLevel = ReminderRiskLevel.OK,
    val title: String = "提醒正常",
    val message: String? = null,
    val nextReminderText: String? = null,
    val showOpenSettingsAction: Boolean = false,
)
```

UI 文案建议：

- `OK`：提醒已准备好
- `WARNING`：建议检查后台和通知设置
- `BLOCKED`：提醒可能无法生效

### 12.3 `EditEntryTimeUiState`

```kotlin
data class EditEntryTimeUiState(
    val isVisible: Boolean = false,
    val selectedDateMillis: Long? = null,
    val selectedHour: Int = 0,
    val selectedMinute: Int = 0,
    val errorText: String? = null,
    val canConfirm: Boolean = true,
)
```

校验规则：

- 不能选择未来时间
- 当前 session 若命中覆盖周期，不能选到覆盖周期来源出库时间之前

### 12.4 `HistoryUiState`

```kotlin
data class HistoryUiState(
    val isLoading: Boolean = true,
    val items: List<ParkingHistoryItemUiState> = emptyList(),
    val canClear: Boolean = false,
)

data class ParkingHistoryItemUiState(
    val id: String,
    val entryAtText: String,
    val exitAtText: String,
    val durationText: String,
    val feeText: String,
    val statusText: String,
)
```

`statusText` 示例：

- `普通计费`
- `已缴费覆盖`
- `覆盖后重新计费`

### 12.5 `SettingsUiState`

```kotlin
data class SettingsUiState(
    val notificationEnabled: SettingCheckState = SettingCheckState.UNKNOWN,
    val exactAlarmAvailable: SettingCheckState = SettingCheckState.UNKNOWN,
    val batteryOptimizationIgnored: SettingCheckState = SettingCheckState.UNKNOWN,
    val startupHintVisible: Boolean = false,
    val lastScheduleStatusText: String? = null,
    val nextReminderText: String? = null,
)

enum class SettingCheckState {
    OK,
    WARNING,
    BLOCKED,
    UNKNOWN,
}
```

### 12.6 `ReminderAlertUiState`

```kotlin
data class ReminderAlertUiState(
    val targetFeeText: String,
    val parkingDurationText: String,
    val actionText: String = "请尽快缴费离场",
    val canDismiss: Boolean = true,
)
```

首版可以只通过通知打开主页面，不一定实现独立 alert 页。

---

## 13. ViewModel Blueprint

### 13.1 `HomeViewModel`

职责：

- 观察 active session
- 观察 reminder health
- 每分钟或每秒刷新一次 `now`
- 组装 `HomeUiState`
- 调用 `ParkingCommandService`

接口草图：

```kotlin
class HomeViewModel(
    private val parkingRepository: ParkingRepository,
    private val parkingCommandService: ParkingCommandService,
    private val parkingStateResolver: ParkingStateResolver,
    private val reminderHealthService: ReminderHealthService,
    private val clock: AppClock,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState>

    fun onStartParkingClicked()

    fun onCheckoutClicked()

    fun onEditEntryTimeConfirmed(entryAt: Instant)

    fun onOpenReminderSettingsClicked()
}
```

刷新频率建议：

- 活动停车时每秒刷新倒计时
- Idle 时不需要持续刷新

### 13.2 `HistoryViewModel`

```kotlin
class HistoryViewModel(
    private val parkingRepository: ParkingRepository,
) : ViewModel() {
    val uiState: StateFlow<HistoryUiState>

    fun onClearHistoryClicked()
}
```

### 13.3 `SettingsViewModel`

```kotlin
class SettingsViewModel(
    private val reminderHealthService: ReminderHealthService,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState>

    fun onOpenNotificationSettingsClicked()

    fun onOpenExactAlarmSettingsClicked()

    fun onOpenBatterySettingsClicked()
}
```

---

## 14. Navigation Blueprint

首版推荐简单三页结构：

```text
Home
History
Settings
```

路由：

```kotlin
sealed interface AppRoute {
    data object Home : AppRoute
    data object History : AppRoute
    data object Settings : AppRoute
}
```

如果引入 Navigation Compose，路径建议：

```text
home
history
settings
```

也可以首版先不用 Navigation Compose，采用一个根状态控制当前 tab。等页面复杂后再引入导航库。

---

## 15. Dependency Wiring

首版不引入 Hilt / Koin，使用轻量手写容器。

### 15.1 `ParkingReminderApp`

```kotlin
class ParkingReminderApp : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
```

### 15.2 `AppContainer`

```kotlin
class AppContainer(
    private val context: Context,
) {
    val database: AppDatabase = createDatabase(context)
    val clock: AppClock = SystemAppClock()

    val parkingRepository: ParkingRepository = RoomParkingRepository(...)
    val reminderStateRepository: ReminderStateRepository = RoomReminderStateRepository(...)

    val billingCalculator = BillingCalculator()
    val reminderPlanner = ReminderPlanner()
    val parkingStateResolver = ParkingStateResolver(billingCalculator, reminderPlanner)

    val reminderScheduler: ReminderScheduler = ReminderAlarmScheduler(...)
    val reminderResyncService = ReminderResyncService(...)
    val parkingCommandService = ParkingCommandService(...)
    val reminderHealthService = ReminderHealthService(...)
}
```

Receiver 获取依赖：

- 通过 `context.applicationContext as ParkingReminderApp`
- 从 `appContainer` 取 `reminderResyncService`

注意：

- Receiver 内部执行 suspend 逻辑时要使用 `goAsync()`
- 在后台 Receiver 中不要启动长时间任务
- 提醒处理应快速完成，必要时只做通知和下一条提醒同步

---

## 16. First Coding Milestones

建议按以下顺序落代码：

1. 添加 Room、ViewModel、Navigation 相关依赖
2. 创建 domain model 和 rule classes
3. 为 `BillingCalculator`、`ReminderPlanner` 写单元测试
4. 创建 Room entities、DAO、Database
5. 实现 `RoomParkingRepository`
6. 实现 `ReminderStateRepository`
7. 实现 `ReminderAlarmScheduler`
8. 实现 `ReminderResyncService`
9. 接入 `ParkingCommandService`
10. 实现 `HomeViewModel` 和最小 Home UI
11. 接通知权限和 notification channel
12. 接 boot/time/timezone/package receivers
13. 做 Redmi K50 真机提醒验证

---

## 17. MVP 必测规则样例

`BillingCalculator` 单测至少覆盖：

| 场景 | now | 期望费用 |
|------|-----|----------|
| 入库后 59m59s | `entry + 59m59s` | `0` |
| 入库刚满 1h | `entry + 1h` | `5` |
| 入库 11h59m59s | `entry + 11h59m59s` | `5` |
| 入库刚满 12h | `entry + 12h` | `10` |
| 覆盖期内 | `coverageEnd - 1ms` | `0` |
| 覆盖期刚结束 | `coverageEnd` | `5` |
| 覆盖期结束后刚满 12h | `coverageEnd + 12h` | `10` |

`ReminderPlanner` 单测至少覆盖：

| 场景 | 期望提醒 |
|------|----------|
| 普通停车刚开始 | `entry + 50m` |
| 已过 50m 但未到 12h | `entry + 11h50m` |
| 覆盖期内再次入库 | `coverageEnd - 10m` |
| 覆盖期结束后 | `coverageEnd + 11h50m` |
| 用户修改入库时间后 | 基于新时间重新计算 |

---

## 18. 暂缓项

以下内容不进入首轮蓝图实现：

- 多车辆
- 自定义城市规则
- 云同步
- 自定义铃声管理
- 未确认前重复响铃
- 全屏强提醒
- 正式应用商店上架策略

这些都可以后续迭代，但不应该阻塞 MVP 的核心提醒链路。
