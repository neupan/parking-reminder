package com.neupan.parking_reminder.ui

import android.Manifest
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neupan.parking_reminder.AppContainer
import com.neupan.parking_reminder.ui.home.HomeScreen
import com.neupan.parking_reminder.ui.home.HomeViewModel
import kotlinx.coroutines.delay

@Composable
fun ParkingReminderRoot(
    appContainer: AppContainer,
) {
    RequestNotificationPermission()
    val context = LocalContext.current
    val ringtonePrefs = appContainer.ringtonePreferences
    val notifier = appContainer.reminderNotifier

    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(appContainer),
    )

    var ringtoneRefreshKey by remember { mutableIntStateOf(0) }
    val ringtoneTitle = remember(ringtoneRefreshKey) {
        ringtonePrefs.getAlarmTitle(context)
    }

    var isAlarmPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            isAlarmPlaying = notifier.isAlarmPlaying
            delay(500)
        }
    }

    val ringtonePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data
            ?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (uri != null) {
            ringtonePrefs.setAlarmUri(uri)
            ringtoneRefreshKey++
        }
    }

    HomeScreen(
        viewModel = viewModel,
        onOpenExactAlarmSettings = {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
            context.startActivity(intent)
        },
        onPickRingtone = {
            val currentUri = ringtonePrefs.getAlarmUriResolved(context)
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择提醒铃声")
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
            }
            ringtonePicker.launch(intent)
        },
        alarmRingtoneTitle = ringtoneTitle,
        isAlarmPlaying = isAlarmPlaying,
    )
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PermissionChecker.PERMISSION_GRANTED
        if (!granted) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
