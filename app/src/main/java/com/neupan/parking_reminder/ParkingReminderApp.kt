package com.neupan.parking_reminder

import android.app.Application

class ParkingReminderApp : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
