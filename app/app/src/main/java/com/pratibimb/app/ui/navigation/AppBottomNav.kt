package com.pratibimb.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratibimb.app.ui.theme.WarmObsidian

enum class AppTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    LIVE_TWIN("Live Twin", Icons.Default.Psychology),
    TRAY("Tray", Icons.Default.Archive),
    REPORT("Report", Icons.Default.Assessment),
    SETTINGS("Settings", Icons.Default.Tune),
}

@Composable
fun AppBottomNav(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    trayCount: Int,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = WarmObsidian.SurfaceContainer,
        tonalElevation = 0.dp,
    ) {
        AppTab.entries.forEach { tab ->
            val isSelected = selectedTab == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    if (tab == AppTab.TRAY && trayCount > 0) {
                        BadgedBox(
                            badge = {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(WarmObsidian.Primary)
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "$trayCount",
                                        color = WarmObsidian.OnPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        ) {
                            Icon(imageVector = tab.icon, contentDescription = tab.title)
                        }
                    } else {
                        Icon(imageVector = tab.icon, contentDescription = tab.title)
                    }
                },
                label = {
                    Text(
                        text = tab.title,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = WarmObsidian.OnSecondaryContainer,
                    selectedTextColor = WarmObsidian.OnSurface,
                    indicatorColor = WarmObsidian.SecondaryContainer,
                    unselectedIconColor = WarmObsidian.Outline,
                    unselectedTextColor = WarmObsidian.OnSurfaceVariant,
                ),
            )
        }
    }
}
