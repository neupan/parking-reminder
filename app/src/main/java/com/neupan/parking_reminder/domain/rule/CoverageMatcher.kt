package com.neupan.parking_reminder.domain.rule

import com.neupan.parking_reminder.domain.model.CoverageWindow
import java.time.Instant

class CoverageMatcher {
    fun isCovered(entryAt: Instant, window: CoverageWindow): Boolean {
        return window.isActive &&
            !entryAt.isBefore(window.startAt) &&
            entryAt.isBefore(window.endAt)
    }
}
