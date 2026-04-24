package com.neupan.parking_reminder.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreenContent(
        uiState = uiState,
        onStartParking = viewModel::onStartParkingClicked,
        onCheckout = viewModel::onCheckoutClicked,
    )
}

@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    onStartParking: () -> Unit,
    onCheckout: () -> Unit,
) {
    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .padding(innerPadding),
        ) {
            if (uiState.isLoading) {
                LoadingContent()
                return@Box
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Header()

                StatusPanel(uiState)

                ActionPanel(
                    uiState = uiState,
                    onStartParking = onStartParking,
                    onCheckout = onCheckout,
                )

                ReminderPanel(uiState.reminderHealth)
            }
        }
    }
}

@Composable
private fun Header() {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "停车缴费提醒",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "下一次加费前 10 分钟提醒",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun StatusPanel(uiState: HomeUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = PanelColor),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = uiState.primaryText,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = uiState.secondaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }

                FeeBadge(uiState.currentFeeYuan)
            }

            if (uiState.countdownText != null) {
                CountdownBlock(uiState)
            } else {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = "准备好后即可开始记录本次停车",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.74f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun FeeBadge(feeYuan: Int) {
    Surface(
        shape = CircleShape,
        color = AccentColor.copy(alpha = 0.18f),
        contentColor = AccentColor,
    ) {
        Box(
            modifier = Modifier.size(86.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${feeYuan}元",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CountdownBlock(uiState: HomeUiState) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = uiState.countdownText.orEmpty(),
            style = MaterialTheme.typography.displayMedium,
            color = urgencyColor(uiState.urgency),
            fontWeight = FontWeight.Bold,
        )
        LinearProgressIndicator(
            progress = { uiState.progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = urgencyColor(uiState.urgency),
            trackColor = Color.White.copy(alpha = 0.14f),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = uiState.entryAtText.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f),
            )
            Text(
                text = uiState.nextChangeAtText.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f),
            )
        }
    }
}

@Composable
private fun ActionPanel(
    uiState: HomeUiState,
    onStartParking: () -> Unit,
    onCheckout: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            enabled = uiState.canStartParking && !uiState.actionInProgress,
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentColor,
                contentColor = Color(0xFF06201B),
            ),
            shape = RoundedCornerShape(8.dp),
            onClick = onStartParking,
        ) {
            Text(
                text = "开始泊车",
                fontWeight = FontWeight.Bold,
            )
        }

        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            enabled = uiState.canCheckout && !uiState.actionInProgress,
            shape = RoundedCornerShape(8.dp),
            onClick = onCheckout,
        ) {
            Text("已缴费出库")
        }
    }
}

@Composable
private fun ReminderPanel(reminderHealth: ReminderHealthUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = PanelColor.copy(alpha = 0.84f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(reminderLevelColor(reminderHealth.level)),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = reminderHealth.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                val detail = reminderHealth.nextReminderText ?: reminderHealth.message
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.64f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = AccentColor)
    }
}

private fun urgencyColor(urgency: UrgencyLevel): Color {
    return when (urgency) {
        UrgencyLevel.SAFE -> AccentColor
        UrgencyLevel.WARNING -> WarningColor
        UrgencyLevel.URGENT -> UrgentColor
    }
}

private fun reminderLevelColor(level: ReminderHealthLevel): Color {
    return when (level) {
        ReminderHealthLevel.OK -> AccentColor
        ReminderHealthLevel.WARNING -> WarningColor
        ReminderHealthLevel.BLOCKED -> UrgentColor
    }
}

private val AppBackground = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF061512),
        Color(0xFF102924),
        Color(0xFF111827),
    ),
)

private val PanelColor = Color(0xFF15221F)
private val AccentColor = Color(0xFF5EEAD4)
private val WarningColor = Color(0xFFFBBF24)
private val UrgentColor = Color(0xFFF87171)
