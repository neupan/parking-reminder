package com.neupan.parking_reminder.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neupan.parking_reminder.AppContainer
import com.neupan.parking_reminder.alarm.ReminderResyncService
import com.neupan.parking_reminder.alarm.model.ReminderSyncReason
import com.neupan.parking_reminder.domain.model.BillingQuote
import com.neupan.parking_reminder.domain.model.ParkingSession
import com.neupan.parking_reminder.domain.model.ParkingStatus
import com.neupan.parking_reminder.domain.repository.ParkingRepository
import com.neupan.parking_reminder.domain.repository.ReminderScheduleState
import com.neupan.parking_reminder.domain.repository.ReminderScheduleStatus
import com.neupan.parking_reminder.domain.repository.ReminderStateRepository
import com.neupan.parking_reminder.domain.rule.ParkingRuleConfig
import com.neupan.parking_reminder.domain.rule.ParkingStateResolver
import com.neupan.parking_reminder.domain.service.ParkingCommandService
import com.neupan.parking_reminder.domain.time.AppClock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val parkingRepository: ParkingRepository,
    private val reminderStateRepository: ReminderStateRepository,
    private val parkingCommandService: ParkingCommandService,
    private val reminderResyncService: ReminderResyncService,
    private val parkingStateResolver: ParkingStateResolver,
    private val ruleConfig: ParkingRuleConfig,
    private val clock: AppClock,
) : ViewModel() {
    private val nowFlow = flow {
        while (true) {
            emit(clock.now())
            delay(1_000)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        nowFlow,
        parkingRepository.observeActiveSession(),
        reminderStateRepository.observeScheduleState(),
    ) { now, session, reminderState ->
        UiInputs(
            now = now,
            session = session,
            reminderState = reminderState,
        )
    }
        .map { buildUiState(it.now, it.session, it.reminderState) }
        .catch {
            emit(
                HomeUiState(
                    isLoading = false,
                    reminderPolicyText = reminderPolicyText(),
                    ruleModeText = ruleModeText(),
                    errorMessage = it.message,
                ),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    init {
        viewModelScope.launch {
            reminderResyncService.resync(ReminderSyncReason.APP_COLD_START)
        }
    }

    fun onStartParkingClicked() {
        viewModelScope.launch {
            runCatching { parkingCommandService.startParking() }
        }
    }

    fun onCheckoutClicked() {
        viewModelScope.launch {
            runCatching { parkingCommandService.checkout() }
        }
    }

    private suspend fun buildUiState(
        now: Instant,
        session: ParkingSession?,
        reminderState: ReminderScheduleState?,
    ): HomeUiState {
        if (session == null) {
            return HomeUiState(
                isLoading = false,
                mode = HomeMode.Idle,
                primaryText = "还未开始泊车",
                secondaryText = "到车库后点一下开始，提醒会自动安排",
                reminderPolicyText = reminderPolicyText(),
                ruleModeText = ruleModeText(),
                reminderHealth = reminderState.toReminderHealthUiState(),
                canStartParking = true,
                canCheckout = false,
                canEditEntryTime = false,
            )
        }

        val coverageWindow = session.matchedCoverageWindowId
            ?.let { parkingRepository.getCoverageWindow(it) }
            ?: parkingRepository.findCoveringWindow(session.entryAt)
        val snapshot = parkingStateResolver.resolve(
            now = now,
            activeSession = session,
            matchedCoverageWindow = coverageWindow,
        )
        val quote = snapshot.billingQuote
            ?: return HomeUiState(isLoading = false, errorMessage = "停车状态读取失败")

        return quote.toHomeUiState(
            now = now,
            session = session,
            reminderState = reminderState,
        )
    }

    private fun BillingQuote.toHomeUiState(
        now: Instant,
        session: ParkingSession,
        reminderState: ReminderScheduleState?,
    ): HomeUiState {
        val nextChangeAt = nextChargeAt
        val remaining = nextChangeAt?.let { Duration.between(now, it).coerceAtLeast(Duration.ZERO) }
        val mode = status.toHomeMode()

        return HomeUiState(
            isLoading = false,
            mode = mode,
            currentFeeYuan = currentFeeYuan,
            primaryText = primaryText(),
            secondaryText = secondaryText(),
            reminderPolicyText = reminderPolicyText(),
            ruleModeText = ruleModeText(),
            entryAtText = "入库 ${session.entryAt.formatTime()}",
            nextChangeAtText = nextChangeAt?.let { "下次变化 ${it.formatTime()}" },
            countdownText = remaining?.formatDuration(),
            progressFraction = progressFraction(
                status = status,
                session = session,
                now = now,
            ),
            urgency = remaining.toUrgencyLevel(),
            reminderHealth = reminderState.toReminderHealthUiState(),
            canStartParking = false,
            canCheckout = true,
            canEditEntryTime = true,
        )
    }

    private fun BillingQuote.primaryText(): String {
        return when (status) {
            ParkingStatus.Idle -> "还未开始泊车"
            is ParkingStatus.ParkingFree -> "免费停车中"
            is ParkingStatus.ParkingCharged -> "当前需缴费 $currentFeeYuan 元"
            is ParkingStatus.ParkingCovered -> "已缴费覆盖中"
            is ParkingStatus.PostCoverageCharged -> "覆盖已结束，当前 $currentFeeYuan 元"
        }
    }

    private fun BillingQuote.secondaryText(): String {
        return when (val currentStatus = status) {
            ParkingStatus.Idle -> "到车库后点一下开始"
            is ParkingStatus.ParkingFree -> "${ruleConfig.freeDuration.formatHumanDuration()}免费期内"
            is ParkingStatus.ParkingCharged -> "下次将加费到 ${currentStatus.nextFeeYuan} 元"
            is ParkingStatus.ParkingCovered -> "覆盖到 ${currentStatus.coverageWindow.endAt.formatTime()}"
            is ParkingStatus.PostCoverageCharged -> "下次将加费到 ${currentStatus.nextFeeYuan} 元"
        }
    }

    private fun ParkingStatus.toHomeMode(): HomeMode {
        return when (this) {
            ParkingStatus.Idle -> HomeMode.Idle
            is ParkingStatus.ParkingFree -> HomeMode.ParkingFree
            is ParkingStatus.ParkingCharged -> HomeMode.ParkingCharged
            is ParkingStatus.ParkingCovered -> HomeMode.ParkingCovered
            is ParkingStatus.PostCoverageCharged -> HomeMode.PostCoverageCharged
        }
    }

    private fun progressFraction(
        status: ParkingStatus,
        session: ParkingSession,
        now: Instant,
    ): Float {
        val interval = when (status) {
            ParkingStatus.Idle -> return 0f
            is ParkingStatus.ParkingFree -> session.entryAt to status.freeEndsAt
            is ParkingStatus.ParkingCharged -> chargedInterval(session.entryAt, status.nextChargeAt)
            is ParkingStatus.ParkingCovered -> session.entryAt to status.coverageWindow.endAt
            is ParkingStatus.PostCoverageCharged -> {
                status.nextChargeAt.minus(ruleConfig.billingCycle) to status.nextChargeAt
            }
        }
        val total = Duration.between(interval.first, interval.second).toMillis().coerceAtLeast(1)
        val elapsed = Duration.between(interval.first, now).toMillis().coerceIn(0, total)
        return elapsed.toFloat() / total.toFloat()
    }

    private fun chargedInterval(entryAt: Instant, nextChargeAt: Instant): Pair<Instant, Instant> {
        val firstCycleEnd = entryAt.plus(ruleConfig.billingCycle)
        return if (nextChargeAt == firstCycleEnd) {
            entryAt.plus(ruleConfig.freeDuration) to firstCycleEnd
        } else {
            nextChargeAt.minus(ruleConfig.billingCycle) to nextChargeAt
        }
    }

    private fun ReminderScheduleState?.toReminderHealthUiState(): ReminderHealthUiState {
        return when (this?.status) {
            ReminderScheduleStatus.SCHEDULED -> ReminderHealthUiState(
                level = ReminderHealthLevel.OK,
                title = "提醒已安排",
                nextReminderText = triggerAt?.let { "下一次 ${it.formatTime()}" },
            )
            ReminderScheduleStatus.FAILED -> ReminderHealthUiState(
                level = ReminderHealthLevel.BLOCKED,
                title = "提醒注册失败",
                message = failureReason,
                showOpenExactAlarmSettingsAction = failureReason
                    ?.contains("Exact alarm", ignoreCase = true) == true,
            )
            ReminderScheduleStatus.CANCELED -> ReminderHealthUiState(
                level = ReminderHealthLevel.WARNING,
                title = "暂无提醒",
                message = "当前没有活动中的停车提醒",
            )
            ReminderScheduleStatus.FIRED -> ReminderHealthUiState(
                level = ReminderHealthLevel.WARNING,
                title = "提醒已触发",
                message = "正在等待下一次提醒同步",
            )
            ReminderScheduleStatus.NONE, null -> ReminderHealthUiState(
                level = ReminderHealthLevel.WARNING,
                title = "提醒待同步",
            )
        }
    }

    private fun Duration?.toUrgencyLevel(): UrgencyLevel {
        if (this == null) return UrgencyLevel.SAFE
        val urgentThreshold = ruleConfig.reminderLeadTime
        val warningThreshold = ruleConfig.reminderLeadTime.multipliedBy(3)
        return when {
            this <= urgentThreshold -> UrgencyLevel.URGENT
            this <= warningThreshold -> UrgencyLevel.WARNING
            else -> UrgencyLevel.SAFE
        }
    }

    private fun Duration.coerceAtLeast(minimum: Duration): Duration {
        return if (this < minimum) minimum else this
    }

    private fun Duration.formatDuration(): String {
        val totalSeconds = seconds.coerceAtLeast(0)
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val secs = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.CHINA, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.CHINA, "%02d:%02d", minutes, secs)
        }
    }

    private fun reminderPolicyText(): String {
        return "下一次加费前 ${ruleConfig.reminderLeadTime.formatHumanDuration()}提醒"
    }

    private fun ruleModeText(): String? {
        if (!ruleConfig.isTestMode) return null
        return "测试模式：${ruleConfig.reminderLeadTime.formatHumanDuration()}提醒，" +
            "${ruleConfig.freeDuration.formatHumanDuration()}开始计费，" +
            "${ruleConfig.billingCycle.formatHumanDuration()}一轮"
    }

    private fun Duration.formatHumanDuration(): String {
        val totalSeconds = seconds.coerceAtLeast(0)
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val secs = totalSeconds % 60

        return when {
            hours > 0 && minutes > 0 -> "${hours} 小时 ${minutes} 分钟"
            hours > 0 -> "${hours} 小时"
            minutes > 0 && secs > 0 -> "${minutes} 分钟 ${secs} 秒"
            minutes > 0 -> "${minutes} 分钟"
            else -> "${secs} 秒"
        }
    }

    private fun Instant.formatTime(): String {
        return DATE_TIME_FORMATTER.format(atZone(ZoneId.systemDefault()))
    }

    private data class UiInputs(
        val now: Instant,
        val session: ParkingSession?,
        val reminderState: ReminderScheduleState?,
    )

    class Factory(
        private val appContainer: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(
                parkingRepository = appContainer.parkingRepository,
                reminderStateRepository = appContainer.reminderStateRepository,
                parkingCommandService = appContainer.parkingCommandService,
                reminderResyncService = appContainer.reminderResyncService,
                parkingStateResolver = appContainer.parkingStateResolver,
                ruleConfig = appContainer.ruleConfig,
                clock = appContainer.clock,
            ) as T
        }
    }

    companion object {
        private val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    }
}
