package com.neupan.parking_reminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.neupan.parking_reminder.ui.ParkingReminderRoot
import com.neupan.parking_reminder.ui.theme.ParkingreminderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ParkingreminderTheme(
                darkTheme = true,
                dynamicColor = false,
            ) {
                ParkingReminderRoot(
                    appContainer = (application as ParkingReminderApp).appContainer,
                )
            }
        }
    }
}
