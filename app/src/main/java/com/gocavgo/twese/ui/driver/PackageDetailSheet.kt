package com.gocavgo.twese.ui.driver

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gocavgo.twese.data.ClientPackage
import com.gocavgo.twese.data.PackageStatus
import com.gocavgo.twese.data.hasOpenTransfer
import com.gocavgo.twese.ui.common.FullScreenMediaViewer
import com.gocavgo.twese.ui.common.MediaCarousel
import com.gocavgo.twese.ui.common.SmartTimeText
import com.gocavgo.twese.ui.common.isWideScreen
import com.gocavgo.twese.ui.theme.LocalDriversColors
import com.gocavgo.twese.util.PhoneValidation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Velocity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageDetailSheet(
    pkg: ClientPackage,
    onDismiss: () -> Unit,
    onDeliver: (() -> Unit)? = null,
    onTransfer: (() -> Unit)? = null,
    onPickup: (() -> Unit)? = null,
    onCancelTransfer: ((String, String) -> Unit)? = null,
    isCancellingTransfer: Boolean = false,
    isPickingUp: Boolean = false
) {
    val colors = LocalDriversColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isExpanded = sheetState.currentValue == SheetValue.Expanded
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // Intercept runaway upward fling velocity to prevent oscillation between AnchoredDraggable and verticalScroll
    val nestedScrollConnection = remember(sheetState) {
        object : NestedScrollConnection {
            override suspend fun onPreFling(available: Velocity): Velocity {
                val isFlingingUp = available.y < 0
                val targetExpanded = sheetState.targetValue == SheetValue.Expanded
                return if (isFlingingUp && targetExpanded) available else Velocity.Zero
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(colors.divider)
                )
            }
        }
    ) {
        val isWide = isWideScreen()
        val padH = if (isWide) 24.dp else 20.dp
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.90f)
                .nestedScroll(nestedScrollConnection)
                .padding(horizontal = padH)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .verticalScroll(scrollState, enabled = isExpanded)
        ) {
        Spacer(Modifier.height(10.dp))

        // ── Header: ID + status ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(pkg.id, color = colors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            val statusColor = when (pkg.status) {
                PackageStatus.PICKED_UP          -> colors.amber
                PackageStatus.IN_TRANSIT         -> colors.purple
                PackageStatus.ARRIVED_AT_OFFICE  -> colors.purple
                PackageStatus.OUT_FOR_DELIVERY,
                PackageStatus.DELIVERED          -> colors.purple
                PackageStatus.CANCELLED          -> colors.red
                else                             -> colors.textSecondary
            }
            Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.12f)) {
                Text(pkg.status.name.replace("_", " "),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = statusColor, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(pkg.description, color = colors.textSecondary,
            fontSize = 14.sp)

        if (pkg.createdAt.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            DetailTimeRow("Created", pkg.createdAt, Icons.Filled.Schedule,
                colors.textSecondary)
        }

        HorizontalDivider(color = colors.divider,
            modifier = Modifier.padding(vertical = if (isWide) 12.dp else 14.dp))

        // ── Sender / Recipient side-by-side on wide screens ──
        if (isWide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SENDER", color = colors.purple, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    DetailRow("Name", pkg.senderName, Icons.Filled.Person, colors.purple)
                    DetailRow("Phone", PhoneValidation.toDisplayFormat(pkg.senderPhone),
                        Icons.Filled.Phone, colors.purple)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("RECIPIENT", color = colors.purple, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    DetailRow("Name", pkg.recipientName, Icons.Filled.Person, colors.purple)
                    DetailRow("Phone", PhoneValidation.toDisplayFormat(pkg.recipientPhone),
                        Icons.Filled.Phone, colors.purple)
                }
            }
        } else {
            DetailRow("Sender", pkg.senderName, Icons.Filled.Person, colors.purple)
            DetailRow("Sender phone", PhoneValidation.toDisplayFormat(pkg.senderPhone),
                Icons.Filled.Phone, colors.purple)
            Spacer(Modifier.height(8.dp))
            DetailRow("Recipient", pkg.recipientName, Icons.Filled.Person, colors.purple)
            DetailRow("Recipient phone", PhoneValidation.toDisplayFormat(pkg.recipientPhone),
                Icons.Filled.Phone, colors.purple)
        }

        Spacer(Modifier.height(if (isWide) 8.dp else 10.dp))

        // ── Pickup / Destination (always full width) ──
        DetailRow("Pickup", pkg.fromAddress, Icons.Filled.MyLocation, colors.purple)
        DetailRow("Destination", pkg.toAddress, Icons.Filled.LocationOn, colors.textSecondary)

        // ── Media carousel ──
        if (pkg.mediaUrls.isNotEmpty()) {
            Spacer(Modifier.height(if (isWide) 12.dp else 14.dp))
            Text("Media", color = colors.textSecondary, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold)
            var showMaximized by remember { mutableStateOf(false) }
            MediaCarousel(mediaUrls = pkg.mediaUrls,
                onMaximize = { showMaximized = true })
            if (showMaximized) {
                Dialog(onDismissRequest = { showMaximized = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)) {
                    FullScreenMediaViewer(mediaUrls = pkg.mediaUrls,
                        onClose = { showMaximized = false }, showClose = false)
                }
            }
        }

        // ── Weight & count ──
        if (pkg.weight.isNotBlank() || pkg.photoCount > 0) {
            Spacer(Modifier.height(if (isWide) 8.dp else 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (pkg.weight.isNotBlank())
                    DetailRow("Weight", pkg.weight, Icons.Filled.Scale, colors.textSecondary)
                if (pkg.photoCount > 0)
                    DetailRow("Photos", "${pkg.photoCount}", Icons.Filled.Photo,
                        colors.textSecondary)
            }
        }

        // ── Delivery code ──
        if (pkg.deliveryCode.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            DetailRow("Delivery code", pkg.deliveryCode, Icons.Filled.Key, colors.amber)
        }

        // ── Status history ──
        if (pkg.statusHistory.isNotEmpty()) {
            Spacer(Modifier.height(if (isWide) 12.dp else 14.dp))
            Text("Status History", color = colors.textPrimary,
                fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            pkg.statusHistory.reversed().forEach { update ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape)
                        .background(colors.purple.copy(alpha = 0.4f)).offset(y = 4.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(update.message, color = colors.textPrimary, fontSize = 13.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SmartTimeText(update.timestamp,
                                color = colors.textSecondary, fontSize = 12.sp)
                            if (update.location.isNotBlank()) {
                                Text(" • ${update.location}",
                                    color = colors.textSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // ── Action dock ──
        if (pkg.status != PackageStatus.DELIVERED && pkg.status != PackageStatus.CANCELLED) {
            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 12.dp))

            val isPendingConfirmation = pkg.status == PackageStatus.PENDING_CONFIRMATION
            val needsPickup = pkg.backendStatus in listOf("ASSIGNED_DRIVER", "ORIGIN_OFFICE")
            val isTransferOpen = pkg.hasOpenTransfer

            when {
                needsPickup && onPickup != null -> {
                    Button(
                        onClick = { if (!isPickingUp) onPickup() },
                        enabled = !isPickingUp,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.purple)
                    ) {
                        if (isPickingUp) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Picking up...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Filled.LocalShipping, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Pick Up Package", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                isTransferOpen -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.purple.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, colors.purple.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.HourglassTop, null, tint = colors.purple, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Transfer active — awaiting counterparty pickup",
                                    color = colors.purple, fontSize = 13.sp, fontWeight = FontWeight.Medium
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
                                        Text("Cancel Transfer Offer (Recall)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.red)
                                    }
                                }
                            }
                        }
                    }
                }

                onDeliver != null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onDeliver,
                            modifier = Modifier.weight(1.3f).height(44.dp),
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
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!isPendingConfirmation && onTransfer != null) {
                            OutlinedButton(
                                onClick = onTransfer,
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colors.divider)
                            ) {
                                Icon(Icons.Filled.MoveToInbox, null, modifier = Modifier.size(16.dp), tint = colors.purple)
                                Spacer(Modifier.width(4.dp))
                                Text("Transfer", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
    }
}

@Composable
private fun DetailRow(label: String, value: String, icon: ImageVector, iconTint: Color) {
    val colors = LocalDriversColors.current
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, color = colors.textSecondary, fontSize = 12.sp)
            Text(value, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DetailTimeRow(label: String, iso: String, icon: ImageVector, iconTint: Color) {
    val colors = LocalDriversColors.current
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, color = colors.textSecondary, fontSize = 12.sp)
            SmartTimeText(iso.ifBlank { "N/A" },
                color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
