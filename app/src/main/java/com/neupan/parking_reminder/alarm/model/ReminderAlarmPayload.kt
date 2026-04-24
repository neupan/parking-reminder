package com.neupan.parking_reminder.alarm.model

import android.content.Intent
import com.neupan.parking_reminder.domain.model.ReminderPlan
import com.neupan.parking_reminder.domain.model.ReminderType
import java.time.Instant

data class ReminderAlarmPayload(
    val sessionId: String,
    val reminderType: ReminderType,
    val expectedTriggerAtEpochMillis: Long,
    val targetFeeYuan: Int,
) {
    fun toReminderPlan(): ReminderPlan {
        return ReminderPlan(
            sessionId = sessionId,
            reminderType = reminderType,
            triggerAt = Instant.ofEpochMilli(expectedTriggerAtEpochMillis),
            targetFeeYuan = targetFeeYuan,
        )
    }

    fun putInto(intent: Intent): Intent {
        return intent
            .putExtra(EXTRA_SESSION_ID, sessionId)
            .putExtra(EXTRA_REMINDER_TYPE, reminderType.name)
            .putExtra(EXTRA_EXPECTED_TRIGGER_AT, expectedTriggerAtEpochMillis)
            .putExtra(EXTRA_TARGET_FEE_YUAN, targetFeeYuan)
    }

    companion object {
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_REMINDER_TYPE = "reminder_type"
        private const val EXTRA_EXPECTED_TRIGGER_AT = "expected_trigger_at"
        private const val EXTRA_TARGET_FEE_YUAN = "target_fee_yuan"

        fun from(plan: ReminderPlan): ReminderAlarmPayload {
            return ReminderAlarmPayload(
                sessionId = plan.sessionId,
                reminderType = plan.reminderType,
                expectedTriggerAtEpochMillis = plan.triggerAt.toEpochMilli(),
                targetFeeYuan = plan.targetFeeYuan,
            )
        }

        fun from(intent: Intent): ReminderAlarmPayload? {
            val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return null
            val reminderTypeName = intent.getStringExtra(EXTRA_REMINDER_TYPE) ?: return null
            val reminderType = runCatching { ReminderType.valueOf(reminderTypeName) }.getOrNull()
                ?: return null
            val triggerAt = intent.getLongExtra(EXTRA_EXPECTED_TRIGGER_AT, Long.MIN_VALUE)
                .takeUnless { it == Long.MIN_VALUE }
                ?: return null
            val targetFeeYuan = intent.getIntExtra(EXTRA_TARGET_FEE_YUAN, Int.MIN_VALUE)
                .takeUnless { it == Int.MIN_VALUE }
                ?: return null

            return ReminderAlarmPayload(
                sessionId = sessionId,
                reminderType = reminderType,
                expectedTriggerAtEpochMillis = triggerAt,
                targetFeeYuan = targetFeeYuan,
            )
        }
    }
}
