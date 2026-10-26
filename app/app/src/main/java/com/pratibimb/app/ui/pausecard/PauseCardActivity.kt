package com.pratibimb.app.ui.pausecard

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratibimb.app.engine.PauseCardRules
import com.pratibimb.app.engine.PauseCardView
import com.pratibimb.app.engine.TwinRouter
import com.pratibimb.app.ui.theme.PratibimbTheme
import com.pratibimb.app.ui.theme.ShapePill
import com.pratibimb.app.ui.theme.WarmObsidian
import com.pratibimb.app.ui.theme.bodyLg
import com.pratibimb.app.ui.theme.bodyMd
import com.pratibimb.app.ui.theme.cardSurface
import com.pratibimb.app.ui.theme.labelMd
import com.pratibimb.app.ui.theme.titleMd
import kotlinx.coroutines.delay

// Opened by tapping one of our alert pings during focus: what happens if you open it now,
// versus waiting. If the card should not show (too many this session, too soon after the last,
// "Ask me less"), the original notification opens straight away, as it would without the card.
class PauseCardActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TwinRouter.init(this)
        val liveItemId = intent.getStringExtra(EXTRA_LIVE_ID)
        val card = liveItemId?.let { TwinRouter.pauseCardFor(it) }
        if (card == null) {
            liveItemId?.let { TwinRouter.openOriginal(this, it) }
            finish()
            return
        }
        // A recreated activity (rotation) has already counted this card.
        if (savedInstanceState == null) TwinRouter.pauseCardShown(card.liveItemId)
        setContent {
            PratibimbTheme {
                PauseCard(
                    card = card,
                    onStayFocused = {
                        TwinRouter.pauseCardStayFocused(card.liveItemId)
                        finish()
                    },
                    onOpenNow = {
                        TwinRouter.pauseCardOpenAnyway(this, card.liveItemId)
                        finish()
                    },
                    onAskLess = {
                        TwinRouter.pauseCardAskLess(card.liveItemId)
                        finish()
                    },
                    onAutoDismiss = {
                        TwinRouter.pauseCardAutoDismissed(card.liveItemId)
                        finish()
                    },
                    onWorth = { worth -> TwinRouter.recordAlertFeedback(card.liveItemId, card.packageName, card.senderHash, worth) },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_LIVE_ID = "live_item_id"

        // What our ping opens instead of the original app, for an item that may get a card.
        fun pendingIntent(context: Context, liveItemId: String): PendingIntent {
            val intent = Intent(context, PauseCardActivity::class.java)
                .putExtra(EXTRA_LIVE_ID, liveItemId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                context,
                liveItemId.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}

@Composable
private fun PauseCard(
    card: PauseCardView,
    onStayFocused: () -> Unit,
    onOpenNow: () -> Unit,
    onAskLess: () -> Unit,
    onAutoDismiss: () -> Unit,
    onWorth: (Boolean) -> Unit,
) {
    var countdown by remember { mutableStateOf<Int?>(null) }
    var lastInputAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var worthAnswered by remember { mutableStateOf<Boolean?>(null) }

    // Five seconds without a tap closes the card and leaves the notification in the shade.
    LaunchedEffect(countdown, lastInputAt) {
        if (countdown != null) return@LaunchedEffect
        delay(PauseCardRules.AUTO_DISMISS_MILLIS)
        onAutoDismiss()
    }
    LaunchedEffect(countdown) {
        val left = countdown ?: return@LaunchedEffect
        if (left == 0) {
            onOpenNow()
        } else {
            delay(1_000)
            countdown = left - 1
        }
    }

    Column(
        modifier = Modifier
            .width(330.dp)
            .cardSurface()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = "${card.app} · ${card.title}", style = labelMd(color = WarmObsidian.OnSurfaceVariant), maxLines = 1)
        Text(text = "Before you open it", style = titleMd(color = WarmObsidian.OnSurface))
        CardLine("If you open it:", "about ${card.lossLowMin}–${card.lossHighMin} min lost (lower bound)")
        CardLine("If you wait:", "held until your break in ${card.waitMin} min")
        CardLine("Focus left today:", "${card.focusLeftMin} min")
        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onStayFocused,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = ShapePill,
            colors = ButtonDefaults.buttonColors(containerColor = WarmObsidian.PrimaryContainer, contentColor = WarmObsidian.OnPrimary),
        ) {
            Text(text = "Stay focused", fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(
            onClick = { if (countdown == null) countdown = PauseCardRules.OPEN_COUNTDOWN_SECONDS },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = ShapePill,
        ) {
            Text(
                text = countdown?.let { "Opening in $it…" } ?: "Open anyway",
                color = WarmObsidian.OnSurface,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(true to "Worth it", false to "Not worth it").forEach { (worth, label) ->
                OutlinedButton(
                    onClick = {
                        lastInputAt = System.currentTimeMillis()
                        if (worthAnswered == null) {
                            worthAnswered = worth
                            onWorth(worth)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = ShapePill,
                ) {
                    Text(
                        text = if (worthAnswered == worth) "✓ $label" else label,
                        color = if (worthAnswered == worth) WarmObsidian.Primary else WarmObsidian.OnSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        Text(
            text = "Ask me less",
            style = labelMd(color = WarmObsidian.Outline),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(onClick = onAskLess)
                .padding(4.dp),
        )
    }
}

@Composable
private fun CardLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().background(WarmObsidian.SurfaceContainer)) {
        Text(text = label, style = bodyMd(color = WarmObsidian.OnSurfaceVariant), modifier = Modifier.width(118.dp))
        Text(text = value, style = bodyLg(color = WarmObsidian.OnSurface), fontWeight = FontWeight.Medium)
    }
}
