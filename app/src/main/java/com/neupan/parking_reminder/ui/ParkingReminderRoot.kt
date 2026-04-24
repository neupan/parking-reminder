package com.neupan.parking_reminder.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neupan.parking_reminder.AppContainer
import com.neupan.parking_reminder.ui.home.HomeScreen
import com.neupan.parking_reminder.ui.home.HomeViewModel

@Composable
fun ParkingReminderRoot(
    appContainer: AppContainer,
) {
    RequestNotificationPermission()

    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(appContainer),
    )
    HomeScreen(viewModel = viewModel)
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
