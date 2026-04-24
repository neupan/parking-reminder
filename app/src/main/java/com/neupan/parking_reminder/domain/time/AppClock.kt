package com.neupan.parking_reminder.domain.time

import java.time.Instant

interface AppClock {
    fun now(): Instant
}
