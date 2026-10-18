package com.gocavgo.twese.ui.driver

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gocavgo.twese.data.ClientPackage
import com.gocavgo.twese.data.Notice
import com.gocavgo.twese.data.PackageStatus
import com.gocavgo.twese.ui.common.PackageStatusIconBadge
import com.gocavgo.twese.ui.common.StatusIconTooltipBadge
import com.gocavgo.twese.data.hasOpenTransfer
import com.gocavgo.twese.data.hasUnreadNotices
import com.gocavgo.twese.ui.common.FullScreenMediaViewer
import com.gocavgo.twese.ui.theme.LocalDriversColors

/**
 * Origin type for an incoming package item.
 */
enum class IncomingSource {
    ASSIGNED,   // Assigned directly to the driver by dispatch/office
    TRANSFER,   // Targeted incoming handover from another driver/worker
    OFFER       // Open broadcast transfer offer
}

/**
 * Unified representation of an item waiting in the "Incoming" tab.
 */
sealed class IncomingPackageItem {
    abstract val pkg: ClientPackage

    data class Assigned(
        override val pkg: ClientPackage
    ) : IncomingPackageItem()

    data class Transfer(
        override val pkg: ClientPackage,
        val transferId: String,
        val ruleType: String
    ) : IncomingPackageItem()

    data class Offer(
        override val pkg: ClientPackage,
        val transferId: String,
        val ruleType: String
    ) : IncomingPackageItem()
}

/**
 * Base modern package card container with route indicator, metadata chips,
 * and media preview support.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModernPackageCard(
    pkg: ClientPackage,
    onDetail: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    isHighlighted: Boolean = false,
    hasUnread: Boolean = false,
    headerBadge: @Composable () -> Unit,
    actions: @Composable () -> Unit
) {
    val colors = LocalDriversColors.current
    var showMediaDialog by remember { mutableStateOf(false) }

    // Shimmer/pulsing border animation for highlighted / newly added packages
    val infiniteTransition = rememberInfiniteTransition(label = "cardGlow")
    val borderProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = FastOutSlowInEasing),
            RepeatMode.Restart
        ),
        label = "borderProgress"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isHighlighted) (1.5.dp + 1.5.dp * borderProgress) else 1.dp,
        label = "borderWidth"
    )
    val borderColor = if (isHighlighted) colors.purple.copy(alpha = 0.5f + borderProgress * 0.4f) else colors.divider

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(borderWidth, borderColor),
        shadowElevation = if (isHighlighted) 4.dp else 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onDetail,
                onLongClick = onLongPress
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // ── 1. Header: Status Icon + Package ID + Unread Dot + Badges ─
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Leading circular status badge
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.purple.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.LocalShipping,
                        contentDescription = null,
                        tint = colors.purple,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (hasUnread) {
                            val dotTransition = rememberInfiniteTransition(label = "unreadPulse")
                            val dotAlpha by dotTransition.animateFloat(
                                initialValue = 0.35f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    tween(800, easing = FastOutSlowInEasing),
                                    RepeatMode.Reverse
                                ),
                                label = "dotAlpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(colors.red.copy(alpha = dotAlpha))
                            )
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            text = pkg.id,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    if (pkg.description.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = pkg.description,
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Custom badge slot (e.g. IN TRANSIT, ASSIGNED, OFFER, TRANSFER)
                headerBadge()
            }

            Spacer(Modifier.height(12.dp))

            // ── 2. Route Timeline (Origin ➔ Destination) ───────────────────
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.surfaceAlt,
                border = BorderStroke(1.dp, colors.divider.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    // Origin row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.purple)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = pkg.fromAddress.ifBlank { "Pickup Station" },
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Vertical route track
                    Box(
                        modifier = Modifier
                            .padding(start = 3.dp, top = 2.dp, bottom = 2.dp)
                            .width(2.dp)
                            .height(14.dp)
                            .background(colors.divider)
                    )

                    // Destination row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.textSecondary)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = pkg.toAddress.ifBlank { "Destination Station" },
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── 3. Metadata Chips Row ──────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Weight chip
                if (pkg.weight.isNotBlank()) {
                    MetaChip(
                        icon = Icons.Filled.Scale,
                        text = pkg.weight,
                        contentColor = colors.textSecondary
                    )
                    Spacer(Modifier.width(8.dp))
                }

                // Category chip
                if (pkg.category.isNotBlank()) {
                    MetaChip(
                        icon = Icons.Filled.Category,
                        text = pkg.category,
                        contentColor = colors.textSecondary
                    )
                    Spacer(Modifier.width(8.dp))
                }

                // Fragile indicator
                if (pkg.fragile) {
                    MetaChip(
                        icon = Icons.Filled.Warning,
                        text = "Fragile",
                        contentColor = colors.amber,
                        backgroundColor = colors.amber.copy(alpha = 0.12f)
                    )
                    Spacer(Modifier.width(8.dp))
                }

                // Media count thumbnail clicker
                if (pkg.photoCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.purple.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, colors.purple.copy(alpha = 0.25f)),
                        modifier = Modifier.clickable { showMediaDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Filled.Photo,
                                contentDescription = null,
                                tint = colors.purple,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${pkg.photoCount}",
                                color = colors.purple,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Quick details button
                TextButton(
                    onClick = onDetail,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(min = 30.dp)
                ) {
                    Text(
                        text = "Details",
                        color = colors.purple,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = colors.purple,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // ── 4. Action Dock ─────────────────────────────────────────────
            actions()
        }
    }

    if (showMediaDialog) {
        Dialog(
            onDismissRequest = { showMediaDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            FullScreenMediaViewer(
                mediaUrls = pkg.mediaUrls,
                onClose = { showMediaDialog = false },
                showClose = true
            )
        }
    }
}

/**
 * Compact metadata chip for weight, category, fragile, etc.
 */
@Composable
private fun MetaChip(
    icon: ImageVector,
    text: String,
    contentColor: Color,
    backgroundColor: Color = Color.Transparent
) {
    val colors = LocalDriversColors.current
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = if (backgroundColor == Color.Transparent) BorderStroke(1.dp, colors.divider) else null
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = text,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. "ON BOARD" (ACTIVE) PACKAGE CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DriverCurrentPackageCard(
    pkg: ClientPackage,
    onDeliver: () -> Unit,
    onTransfer: () -> Unit,
    onDetail: () -> Unit,
    onLongPress: () -> Unit = {},
    onPickup: () -> Unit = {},
    onCancelTransfer: ((String, String) -> Unit)? = null,
    isCancellingTransfer: Boolean = false,
    isPickingUp: Boolean = false,
    notices: List<Notice> = emptyList()
) {
    val colors = LocalDriversColors.current
    val isDelivered = pkg.status == PackageStatus.DELIVERED
    val isTransferRequested = pkg.transferStatus == "REQUESTED"
    val isTransferOpen = pkg.hasOpenTransfer
    val isUnread = pkg.hasUnreadNotices(notices)

    val statusColor = when (pkg.status) {
        PackageStatus.PICKED_UP -> colors.amber
        PackageStatus.IN_TRANSIT -> colors.purple
        PackageStatus.ARRIVED_AT_OFFICE -> colors.purple
        PackageStatus.PENDING_CONFIRMATION -> colors.amber
        PackageStatus.OUT_FOR_DELIVERY, PackageStatus.DELIVERED -> colors.purple
        else -> colors.textSecondary
    }

    ModernPackageCard(
        pkg = pkg,
        onDetail = onDetail,
        onLongPress = onLongPress,
        hasUnread = isUnread,
        headerBadge = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isTransferOpen) {
                    StatusIconTooltipBadge(
                        title = "Outgoing Transfer",
                        description = "You initiated a transfer offer. Waiting for counterparty driver to accept and scan.",
                        icon = Icons.AutoMirrored.Filled.CompareArrows,
                        color = colors.amber
                    )
                    Spacer(Modifier.width(6.dp))
                }

                PackageStatusIconBadge(
                    status = pkg.status,
                    colorOverride = statusColor
                )
            }
        },
        actions = {
            if (!isDelivered && !isTransferOpen) {
                Spacer(Modifier.height(10.dp))
                val isPendingConfirmation = pkg.status == PackageStatus.PENDING_CONFIRMATION
                val isFixedRoute = pkg.deliveryType == "FIXED_ROUTE"
                val needsPickup = isFixedRoute && pkg.backendStatus in listOf("ASSIGNED_DRIVER", "ORIGIN_OFFICE")
                val needsArrival = isFixedRoute && pkg.backendStatus == "IN_TRANSIT"
                val isAtDestinationOffice = isFixedRoute && pkg.backendStatus == "DESTINATION_OFFICE"

                when {
                    needsPickup -> {
                        Button(
                            onClick = { if (!isPickingUp) onPickup() },
                            enabled = !isPickingUp,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.purple)
                        ) {
                            if (isPickingUp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Picking up...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Filled.LocalShipping, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Pick Up Package", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    needsArrival || !isAtDestinationOffice -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onDeliver,
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.purple
                                )
                            ) {
                                Icon(
                                    if (isPendingConfirmation) Icons.Filled.Lock else Icons.Filled.Check,
                                    null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (isPendingConfirmation) "Enter Code" else "Deliver",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            if (!isPendingConfirmation) {
                                OutlinedButton(
                                    onClick = onTransfer,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, colors.divider)
                                ) {
                                    Icon(
                                        Icons.Filled.MoveToInbox,
                                        null,
                                        modifier = Modifier.size(16.dp),
                                        tint = colors.purple
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        "Transfer",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    isAtDestinationOffice -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.purple.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, colors.purple.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.HourglassTop, null, tint = colors.purple, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Delivered to office — awaiting collection",
                                    color = colors.purple,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            } else if (isTransferOpen) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.purple.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, colors.purple.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.HourglassTop, null, tint = colors.purple, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isTransferRequested) "Awaiting owner confirmation"
                                else "Transfer active — waiting for counterparty pickup",
                                color = colors.purple,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (pkg.transferId != null && onCancelTransfer != null) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { onCancelTransfer(pkg.id, pkg.transferId) },
                                enabled = !isCancellingTransfer,
                                modifier = Modifier.fillMaxWidth().height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, colors.red.copy(alpha = 0.5f))
                            ) {
                                if (isCancellingTransfer) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = colors.red, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Cancelling...", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.red)
                                } else {
                                    Icon(Icons.Filled.Close, null, modifier = Modifier.size(14.dp), tint = colors.red)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Cancel Transfer (Recall)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.red)
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. UNIFIED "INCOMING" PACKAGE CARD (MERGED OFFERS, TRANSFERS & ASSIGNMENTS)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DriverIncomingPackageCard(
    item: IncomingPackageItem,
    onAccept: () -> Unit,
    onDetail: () -> Unit,
    isAccepting: Boolean = false,
    hasActiveTrip: Boolean = true,
    isNew: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalDriversColors.current
    val pkg = item.pkg

    // Distinguish source tag (modern, clean, unified purple branding)
    val (_, sourceColor, sourceIcon) = when (item) {
        is IncomingPackageItem.Assigned -> Triple("ASSIGNED", colors.purple, Icons.Filled.AssignmentInd)
        is IncomingPackageItem.Transfer -> Triple("TRANSFER", colors.purple, Icons.AutoMirrored.Filled.CompareArrows)
        is IncomingPackageItem.Offer -> Triple("OFFER", colors.purple, Icons.Filled.NewReleases)
    }

    // Determine button label and icon based on transfer rule
    val ruleType = when (item) {
        is IncomingPackageItem.Assigned -> "ASSIGNED"
        is IncomingPackageItem.Transfer -> item.ruleType
        is IncomingPackageItem.Offer -> item.ruleType
    }

    val (actionLabel, actionIcon) = when (ruleType) {
        "ASSIGNED" -> "Pick Up Package" to Icons.Filled.LocalShipping
        "SECURE" -> "Accept with Code" to Icons.Filled.Lock
        "CONFIRM" -> "Request to Accept" to Icons.Filled.Send
        else -> "Accept & Load" to Icons.Filled.Check
    }

    // If an offer requires active trip, disable button when no active trip
    val isEnabled = if (item is IncomingPackageItem.Offer) hasActiveTrip && !isAccepting else !isAccepting

    ModernPackageCard(
        pkg = pkg,
        onDetail = onDetail,
        isHighlighted = isNew,
        headerBadge = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Rule lock/hourglass badge for secure/confirm transfers
                if (ruleType == "SECURE") {
                    StatusIconTooltipBadge(
                        title = "Secure Code Required",
                        description = "This transfer requires an exchange of secure verification codes.",
                        icon = Icons.Filled.Lock,
                        color = colors.purple
                    )
                    Spacer(Modifier.width(4.dp))
                } else if (ruleType == "CONFIRM") {
                    StatusIconTooltipBadge(
                        title = "Confirmation Required",
                        description = "Counterparty approval is needed before completing this transfer.",
                        icon = Icons.Filled.HourglassTop,
                        color = colors.amber
                    )
                    Spacer(Modifier.width(4.dp))
                }

                // Source tag icon with click tooltip
                val (sourceTitle, sourceDesc) = when (item) {
                    is IncomingPackageItem.Assigned -> "Assigned Package" to "Assigned to you for pickup from sender or origin station."
                    is IncomingPackageItem.Transfer -> "Incoming Transfer" to "Package being transferred to your vehicle from another driver or station."
                    is IncomingPackageItem.Offer -> "Route Offer" to "Available broadcast offer along your trip. Tap accept to claim."
                }
                StatusIconTooltipBadge(
                    title = sourceTitle,
                    description = sourceDesc,
                    icon = sourceIcon,
                    color = sourceColor
                )
            }
        },
        actions = {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { if (isEnabled) onAccept() },
                enabled = isEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.purple,
                    disabledContainerColor = colors.divider
                )
            ) {
                if (isAccepting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Accepting...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(actionIcon, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(actionLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        modifier = modifier
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. SELECTABLE PACKAGE CARD (FOR BATCH TRANSFER)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectableCurrentPackageCard(
    pkg: ClientPackage,
    isSelected: Boolean,
    canSelect: Boolean,
    onToggle: () -> Unit,
    onDetail: () -> Unit
) {
    val colors = LocalDriversColors.current
    val borderColor = if (isSelected) colors.purple else colors.divider
    val bgColor = if (isSelected) colors.purple.copy(alpha = 0.06f) else colors.surface

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = if (isSelected) BorderStroke(2.dp, colors.purple) else BorderStroke(1.dp, borderColor),
        shadowElevation = if (isSelected) 4.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (canSelect) onToggle() },
                onLongClick = { if (canSelect) onToggle() }
            )
            .alpha(if (canSelect) 1f else 0.5f)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canSelect) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggle() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = colors.purple,
                            uncheckedColor = colors.textSecondary
                        ),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.purple.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.LocalShipping, null, tint = colors.purple, modifier = Modifier.size(18.dp))
                }

                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    Text(pkg.id, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (pkg.description.isNotBlank()) {
                        Text(pkg.description, color = colors.textSecondary, fontSize = 13.sp, maxLines = 1)
                    }
                }

                Surface(shape = RoundedCornerShape(8.dp), color = colors.purple.copy(alpha = 0.12f)) {
                    Text(
                        pkg.status.name.replace("_", " "),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = colors.purple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MyLocation, null, tint = colors.purple, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(pkg.fromAddress, color = colors.textSecondary, fontSize = 13.sp, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(pkg.toAddress, color = colors.textSecondary, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}
