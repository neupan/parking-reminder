package com.neupan.parking_reminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal fun BroadcastReceiver.launchAsync(
    context: Context,
    wakeLockTimeoutMs: Long = DEFAULT_WAKE_LOCK_TIMEOUT_MS,
    block: suspend CoroutineScope.() -> Unit,
) {
    val pendingResult = goAsync()
    val wakeLock = acquireWakeLock(context, wakeLockTimeoutMs)
    Log.d("ReceiverAsync", "launchAsync() receiver=${this::class.simpleName} wakeLock=${wakeLock != null}")
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e("ReceiverAsync", "launchAsync() EXCEPTION in ${this@launchAsync::class.simpleName}", e)
        } finally {
            Log.d("ReceiverAsync", "launchAsync() finished ${this@launchAsync::class.simpleName}")
            releaseWakeLock(wakeLock)
            pendingResult.finish()
        }
    }
}

private fun BroadcastReceiver.acquireWakeLock(
    context: Context,
    timeoutMs: Long,
): PowerManager.WakeLock? {
    return runCatching {
        val powerManager = context.applicationContext.getSystemService(PowerManager::class.java)
        powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "${context.packageName}:${this::class.java.simpleName}",
        ).apply {
            setReferenceCounted(false)
            acquire(timeoutMs)
        }
    }.onFailure {
        Log.e("ReceiverAsync", "acquireWakeLock() failed in ${this::class.simpleName}", it)
    }.getOrNull()
}

private fun releaseWakeLock(wakeLock: PowerManager.WakeLock?) {
    if (wakeLock?.isHeld != true) return
    runCatching {
        wakeLock.release()
    }.onFailure {
        Log.e("ReceiverAsync", "releaseWakeLock() failed", it)
    }
}

private const val DEFAULT_WAKE_LOCK_TIMEOUT_MS = 2 * 60 * 1_000L
