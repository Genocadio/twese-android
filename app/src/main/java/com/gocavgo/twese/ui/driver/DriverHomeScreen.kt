package com.gocavgo.twese.ui.driver

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.gocavgo.twese.ui.common.CachedAvatarImage
import com.gocavgo.twese.ui.common.FluidShapes
import com.gocavgo.twese.ui.common.FrostedFloatingIconButton
import com.gocavgo.twese.ui.common.ProfileQuickMenu
import com.gocavgo.twese.ui.common.SettingsMenu
import com.gocavgo.twese.ui.common.contentMaxWidth
import com.gocavgo.twese.ui.common.fluidBounceClick
import com.gocavgo.twese.ui.common.isWideScreen
import com.gocavgo.twese.ui.theme.LocalDriversColors
import com.gocavgo.twese.viewmodel.AppThemeMode
import com.gocavgo.twese.viewmodel.CompletedTrip
import com.gocavgo.twese.viewmodel.DriverProfile
import com.gocavgo.twese.viewmodel.DriverVehicle
import com.gocavgo.twese.viewmodel.TripViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverHomeScreen(
    viewModel: TripViewModel,
    profile: DriverProfile,
    vehicle: DriverVehicle,
    driverHomeTab: Int = 0,
    isDriverProfileMenuOpen: Boolean,
    isDriverSettingsOpen: Boolean,
    themeMode: AppThemeMode,
    isPipEnabled: Boolean = false,
    completedTrips: List<CompletedTrip> = emptyList(),
    isVehicleMenuOpen: Boolean = false,
    isProfileMenuOpen: Boolean = false,
    isSettingsOpen: Boolean = false,
    defaultPage: String = "trips",
    keepScreenAwake: Boolean = false,
    onCreatePackage: () -> Unit,
    onTabChange: (Int) -> Unit = {},
    onProfileMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onCloseSettings: () -> Unit,
    onLogout: () -> Unit,
    onDismissMenus: () -> Unit,
    onProfileClick: () -> Unit,
    onVehicleClick: () -> Unit = {},
    onEditProfileClick: () -> Unit = {},
    onPipEnabledChange: (Boolean) -> Unit = {},
    onDefaultPageChange: (String) -> Unit = {},
    onKeepScreenAwakeChange: (Boolean) -> Unit = {},
    onNoticesClick: () -> Unit = {},
    noticeCount: Int = 0
) {
    val state by viewModel.state.collectAsState()
    val colors = LocalDriversColors.current
    val wide = isWideScreen()
    val maxW = contentMaxWidth()

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {

        // ── Primary View: DriverPackagesTab (Full screen real estate) ──────
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            val contentMod = if (wide && maxW != Dp.Unspecified) {
                Modifier.widthIn(max = maxW).fillMaxHeight()
            } else {
                Modifier.fillMaxSize()
            }
            Box(modifier = contentMod) {
                DriverPackagesTab(viewModel = viewModel)
            }
        }

        // ── Top-Start: Driver Profile Avatar Button (Frosted floating) ──────
        FrostedFloatingIconButton(
            onClick = onProfileMenuClick,
            shape = FluidShapes.pill,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 2.dp, start = 16.dp)
        ) {
            val avatarUrl = profile.avatarUrl
            if (!avatarUrl.isNullOrBlank()) {
                CachedAvatarImage(
                    remoteUrl = avatarUrl,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    profile.name.firstOrNull()?.uppercase() ?: "D",
                    color = colors.purple,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        // ── Top-End: Notification Bell Button (Frosted floating) ───────────
        FrostedFloatingIconButton(
            onClick = onNoticesClick,
            shape = FluidShapes.pill,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 2.dp, end = 16.dp)
        ) {
            BadgedBox(badge = {
                if (noticeCount > 0) {
                    Badge(containerColor = colors.purple, contentColor = Color.White) {
                        Text("$noticeCount", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = "Notifications",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ── Bottom-End Thumb Zone: Stacked Floating Trip HUD + New Package FAB ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Floating minimisable trip overlay (bubble directly above the [+] FAB)
            FloatingTripOverlay(
                viewModel = viewModel,
                onOpenCreateTrip = { viewModel.openDriverCreateTrip() }
            )

            // New Package [+] FAB
            FloatingActionButton(
                onClick = onCreatePackage,
                modifier = Modifier
                    .size(56.dp)
                    .fluidBounceClick(scaleDown = 0.92f),
                containerColor = colors.purple,
                contentColor = Color.White,
                shape = FluidShapes.pill,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Create Package",
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // ── Shared: overlays, menus, dialogs ──────────────────────────────

        // Overlay scrim
        if (isDriverProfileMenuOpen || isDriverSettingsOpen) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismissMenus
                    )
            )
        }

        // Profile quick menu dropdown (expands from top-start avatar)
        AnimatedVisibility(
            visible = isDriverProfileMenuOpen,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 56.dp),
            enter = expandVertically(tween(250, easing = FastOutSlowInEasing)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
        ) {
            ProfileQuickMenu(
                profile = profile,
                vehicle = vehicle,
                onVehicleClick = onVehicleClick,
                onSettingsClick = { onDismissMenus(); onSettingsClick() },
                onLogoutClick = onLogout,
                onProfileClick = { onDismissMenus(); onProfileClick() }
            )
        }

        // Settings dropdown
        AnimatedVisibility(
            visible = isDriverSettingsOpen,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 56.dp),
            enter = expandVertically(tween(250, easing = FastOutSlowInEasing)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
        ) {
            SettingsMenu(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onClose = onCloseSettings,
                isPipEnabled = isPipEnabled,
                onPipEnabledChange = onPipEnabledChange,
                defaultPage = defaultPage,
                onDefaultPageChange = onDefaultPageChange,
                keepScreenAwake = keepScreenAwake,
                onKeepScreenAwakeChange = onKeepScreenAwakeChange
            )
        }

        // ── Floating new-package toast (shown at top, swipeable to dismiss) ──
        AnimatedVisibility(
            visible = state.showNewPackageToast,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp),
            enter = slideInVertically(tween(300)) { -it / 2 } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(200))
        ) {
            val newPkg = state.newPackageFromSubscription
            if (newPkg != null) {
                var swipeOffset by remember { mutableFloatStateOf(0f) }
                val dismissThreshold = -160f

                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, swipeOffset.roundToInt()) }
                        .alpha(1f + (swipeOffset / 300f).coerceIn(0f, 1f))
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                swipeOffset = (swipeOffset + delta).coerceAtMost(0f)
                            },
                            onDragStopped = {
                                if (swipeOffset < dismissThreshold) {
                                    viewModel.dismissNewPackageToast()
                                }
                                swipeOffset = 0f
                            }
                        )
                ) {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 380.dp)
                            .clickable { viewModel.onNewPackageToastClicked() },
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surface,
                        tonalElevation = 8.dp,
                        shadowElevation = 12.dp,
                        border = BorderStroke(1.5.dp, colors.purple.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colors.purple.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.LocalShipping, null, tint = colors.purple, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("New package available!", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(newPkg.id, color = colors.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text("${newPkg.fromAddress} → ${newPkg.toAddress}", color = colors.textSecondary, fontSize = 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.ChevronRight, null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                LaunchedEffect(newPkg.id) {
                    kotlinx.coroutines.delay(8_000)
                    viewModel.dismissNewPackageToast()
                }
            }
        }

        // ── Package Dialogs & Bottom Sheets ──────────────────────────────────
        val selectedPkg = viewModel.getSelectedDriverPackage()

        if (state.isDeliverDialogOpen && selectedPkg != null) {
            DeliverConfirmationDialog(
                packageId = selectedPkg.id,
                expectedCode = "",
                codeInput = state.deliverCodeInput,
                codeError = state.deliverCodeError,
                onCodeChange = { viewModel.updateDeliverCode(it) },
                onConfirm = { viewModel.confirmDeliver() },
                onDismiss = { viewModel.closeDeliverDialog() },
                isConfirming = state.isConfirmingDelivery
            )
        }

        if (state.isTransferDialogOpen && selectedPkg != null) {
            TransferToOfficeDialog(
                packageId = selectedPkg.id,
                currentStop = state.trip.stops.getOrNull(state.currentStopIndex)?.name ?: "Current location",
                onConfirm = { viewModel.confirmTransfer() },
                onDismiss = { viewModel.closeTransferDialog() },
                isCreating = state.isCreatingTransfer
            )
        }

        if (state.isPackageDetailSheetOpen && selectedPkg != null) {
            PackageDetailSheet(
                pkg = selectedPkg,
                onDismiss = { viewModel.closePackageDetail() },
                onDeliver = {
                    val id = selectedPkg.id
                    viewModel.closePackageDetail()
                    viewModel.openDeliverDialog(id)
                },
                onTransfer = {
                    val id = selectedPkg.id
                    viewModel.closePackageDetail()
                    viewModel.openTransferDialog(id)
                },
                onPickup = {
                    viewModel.pickupPackage(selectedPkg.id)
                },
                onCancelTransfer = { pkgId, tId ->
                    viewModel.cancelTransferOffer(pkgId, tId)
                },
                isCancellingTransfer = state.isCancellingTransfer,
                isPickingUp = state.pickingUpPackageId == selectedPkg.id
            )
        }

        if (state.showBatchTransferDialog) {
            BatchTransferDialog(
                packageCount = state.selectedPackageIds.size,
                selectedRuleType = state.selectedTransferRuleType,
                onRuleTypeChange = { viewModel.setTransferRuleType(it) },
                onConfirm = { viewModel.createBatchTransfer() },
                onDismiss = { viewModel.closeBatchTransferDialog() },
                isCreating = state.isCreatingTransfer
            )
        }
    }
}
