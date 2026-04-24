package com.neupan.parking_reminder.domain.time

import java.time.Instant

class SystemAppClock : AppClock {
    override fun now(): Instant = Instant.now()
}
