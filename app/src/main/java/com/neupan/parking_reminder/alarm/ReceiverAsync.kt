package com.neupan.parking_reminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal fun BroadcastReceiver.launchAsync(
    context: Context,
    block: suspend CoroutineScope.() -> Unit,
) {
    val pendingResult = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } finally {
            pendingResult.finish()
        }
    }
}
