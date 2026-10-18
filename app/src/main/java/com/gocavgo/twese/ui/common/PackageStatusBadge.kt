package com.gocavgo.twese.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gocavgo.twese.data.PackageStatus
import com.gocavgo.twese.ui.theme.LocalDriversColors

data class StatusInfo(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun getPackageStatusInfo(status: PackageStatus, isStale: Boolean = false): StatusInfo {
    val colors = LocalDriversColors.current
    return when (status) {
        PackageStatus.PENDING -> StatusInfo(
            title = if (isStale) "Stale / Pending" else "Pending Pickup",
            description = "Order created. Awaiting driver assignment and pickup.",
            icon = if (isStale) Icons.Filled.HourglassEmpty else Icons.Filled.Schedule,
            color = if (isStale) colors.amber.copy(alpha = 0.6f) else colors.amber
        )
        PackageStatus.PICKED_UP -> StatusInfo(
            title = "Picked Up",
            description = "Package has been collected and loaded with the driver.",
            icon = Icons.Filled.Inventory,
            color = colors.purple
        )
        PackageStatus.IN_TRANSIT -> StatusInfo(
            title = "In Transit",
            description = "Currently on route moving toward destination.",
            icon = Icons.Filled.LocalShipping,
            color = colors.purple
        )
        PackageStatus.ARRIVED_AT_OFFICE -> StatusInfo(
            title = "At Station Office",
            description = "Arrived at destination hub station. Ready for collection or onward leg.",
            icon = Icons.Filled.Store,
            color = colors.purple
        )
        PackageStatus.OUT_FOR_DELIVERY -> StatusInfo(
            title = "Out for Delivery",
            description = "Driver is on the final leg approaching recipient address.",
            icon = Icons.Filled.Moped,
            color = colors.purple
        )
        PackageStatus.PENDING_CONFIRMATION -> StatusInfo(
            title = "Awaiting Confirmation",
            description = "Driver has arrived. Waiting for recipient PIN code or delivery confirmation.",
            icon = Icons.Filled.Lock,
            color = colors.purple
        )
        PackageStatus.DELIVERED -> StatusInfo(
            title = "Delivered",
            description = "Package has been successfully handed over to the recipient.",
            icon = Icons.Filled.CheckCircle,
            color = colors.purple
        )
        PackageStatus.CANCELLED -> StatusInfo(
            title = "Cancelled",
            description = "Delivery was cancelled by sender or system.",
            icon = Icons.Filled.Cancel,
            color = colors.red
        )
    }
}

/**
 * Modern icon-only status badge.
 * Replaces text badges (e.g. "PENDING", "IN_TRANSIT").
 * Clicking the icon displays a tooltip popup explaining the meaning of the status.
 */
@Composable
fun PackageStatusIconBadge(
    status: PackageStatus,
    modifier: Modifier = Modifier,
    isStale: Boolean = false,
    colorOverride: Color? = null,
    size: Dp = 28.dp
) {
    val info = getPackageStatusInfo(status, isStale)
    val color = colorOverride ?: info.color
    StatusIconTooltipBadge(
        title = info.title,
        description = info.description,
        icon = info.icon,
        color = color,
        modifier = modifier,
        size = size
    )
}

/**
 * General status icon badge with click-to-explain tooltip (for custom tags like ASSIGNED, TRANSFER, OFFER).
 */
@Composable
fun StatusIconTooltipBadge(
    title: String,
    description: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    val colors = LocalDriversColors.current
    var showTooltip by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = color.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
            modifier = Modifier
                .size(size)
                .clickable { showTooltip = true }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(size * 0.58f)
                )
            }
        }

        DropdownMenu(
            expanded = showTooltip,
            onDismissRequest = { showTooltip = false },
            modifier = Modifier
                .widthIn(min = 220.dp, max = 290.dp)
                .background(colors.surface),
            shape = RoundedCornerShape(12.dp),
            containerColor = colors.surface,
            border = BorderStroke(1.dp, colors.divider)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = title,
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = description,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
