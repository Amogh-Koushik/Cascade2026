package com.pratibimb.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratibimb.app.ui.theme.ShapeCard
import com.pratibimb.app.ui.theme.ShapePill
import com.pratibimb.app.ui.theme.WarmObsidian
import com.pratibimb.app.ui.theme.bodyLg
import com.pratibimb.app.ui.theme.bodyMd

@Composable
fun SetupResultModeBScreen(
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WarmObsidian.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        // Centered Calm Honest Fallback Card & Information
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // One amber-tinted surface container card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapeCard)
                    .background(WarmObsidian.SurfaceContainer)
                    .border(
                        width = 1.dp,
                        color = WarmObsidian.Secondary.copy(alpha = 0.5f),
                        shape = ShapeCard,
                    )
                    .padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = WarmObsidian.Secondary,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(top = 2.dp),
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = "Holds can't stop the buzz. Real apps may still buzz once.",
                        style = bodyLg(color = WarmObsidian.OnSurface),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        lineHeight = 24.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Beneath the card: "Pratibimb will still hold and release notifications."
            Text(
                text = "Pratibimb will still hold and release notifications.",
                style = bodyMd(color = WarmObsidian.OnSurfaceVariant),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
        }

        // Sticky Bottom Bar: Exactly one primary filled button "Start"
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = ShapePill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarmObsidian.PrimaryContainer,
                    contentColor = WarmObsidian.OnPrimary,
                ),
            ) {
                Text(
                    text = "Start",
                    style = bodyLg(color = WarmObsidian.OnPrimary),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                )
            }
        }
    }
}
