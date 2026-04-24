package com.neupan.parking_reminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal fun BroadcastReceiver.launchAsync(
    context: Context,
    block: suspend CoroutineScope.() -> Unit,
) {
    val pendingResult = goAsync()
    Log.d("ReceiverAsync", "launchAsync() receiver=${this::class.simpleName}")
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e("ReceiverAsync", "launchAsync() EXCEPTION in ${this@launchAsync::class.simpleName}", e)
        } finally {
            Log.d("ReceiverAsync", "launchAsync() finished ${this@launchAsync::class.simpleName}")
            pendingResult.finish()
        }
    }
}
