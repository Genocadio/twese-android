package com.gocavgo.twese.ui.driver

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gocavgo.twese.data.Notice
import com.gocavgo.twese.network.BackendStorage
import com.gocavgo.twese.viewmodel.AppThemeMode
import com.gocavgo.twese.viewmodel.CompletedTrip
import com.gocavgo.twese.viewmodel.DriverProfile
import com.gocavgo.twese.viewmodel.DriverVehicle
import com.gocavgo.twese.ui.common.*
import com.gocavgo.twese.ui.theme.CavgoGreen
import com.gocavgo.twese.ui.theme.LocalDriversColors
import com.gocavgo.twese.viewmodel.TripViewModel
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

enum class DockWindowTab {
    PROFILE,
    SEARCH,
    NEW_PACKAGES,
    TRIP,
    NOTIFICATIONS
}

@Composable
fun DriverHomeScreen(
    viewModel: TripViewModel,
    profile: DriverProfile,
    vehicle: DriverVehicle?,
    driverHomeTab: Int = 0,
    isDriverProfileMenuOpen: Boolean = false,
    isDriverSettingsOpen: Boolean = false,
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    isPipEnabled: Boolean = false,
    completedTrips: List<CompletedTrip> = emptyList(),
    isVehicleMenuOpen: Boolean = false,
    isProfileMenuOpen: Boolean = false,
    isSettingsOpen: Boolean = false,
    defaultPage: String = "trips",
    keepScreenAwake: Boolean = false,
    onCreatePackage: () -> Unit = {},
    onTrackPackage: () -> Unit = {},
    onLogout: () -> Unit = {},
    onTabChange: (Int) -> Unit = {},
    onProfileMenuClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onVehicleClick: () -> Unit = {},
    onThemeModeChange: (AppThemeMode) -> Unit = {},
    onCloseSettings: () -> Unit = {},
    onDismissMenus: () -> Unit = {},
    onEditProfileClick: () -> Unit = {},
    onCompletedTripsClick: () -> Unit = {},
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

    var activeDockTab by remember { mutableStateOf<DockWindowTab?>(null) }
    var showAddChoiceDialog by remember { mutableStateOf(false) }

    val activeTrip = state.activeDriverTrip
    val hasActiveTrip = state.hasActiveTrip || activeTrip != null

    // ── Incoming Items Computation for NEW_PACKAGES ─────────────────────────
    val NOT_PICKED_UP_BACKEND_STATUSES = remember { setOf("ORIGIN_OFFICE", "ASSIGNED_DRIVER") }
    val assignedToDriverPackages = state.driverCurrentPackages.filter {
        it.backendStatus in NOT_PICKED_UP_BACKEND_STATUSES
    }
    val incomingPackageUuids = remember(state.driverIncomingTransfers) {
        state.driverIncomingTransfers.flatMap { it.packages.map { pkg -> pkg.packageUuid } }.toSet()
    }
    val driverUserId = state.authUser?.id
    val visibleOffers = state.driverAvailableOffers.filter { pkg ->
        val alreadyCustodian = driverUserId != null && pkg.custodians.any { it.userId == driverUserId }
        pkg.transferId != null && !alreadyCustodian && pkg.transferStatus != "REQUESTED"
    }
    val incomingItems: List<IncomingPackageItem> = remember(
        assignedToDriverPackages,
        state.driverIncomingTransfers,
        visibleOffers,
        incomingPackageUuids
    ) {
        val list = mutableListOf<IncomingPackageItem>()
        assignedToDriverPackages.forEach { list.add(IncomingPackageItem.Assigned(it)) }
        state.driverIncomingTransfers.forEach { matched ->
            matched.packages.forEach { pkg ->
                list.add(IncomingPackageItem.Transfer(pkg, matched.transferId, matched.ruleType))
            }
        }
        visibleOffers.filter { it.packageUuid !in incomingPackageUuids }.forEach { pkg ->
            list.add(IncomingPackageItem.Offer(pkg, pkg.transferId ?: "", pkg.transferRuleType ?: "AUTO"))
        }
        list
    }

    val unreadNoticeCount = remember(state.notices) {
        state.notices.count { it.viewerReadAt == null }
    }

    // React to external triggers
    LaunchedEffect(isDriverProfileMenuOpen, isDriverSettingsOpen) {
        if (isDriverProfileMenuOpen || isDriverSettingsOpen) {
            activeDockTab = DockWindowTab.PROFILE
        }
    }
    LaunchedEffect(state.driverPackageSubTab) {
        if (state.driverPackageSubTab == 1) {
            activeDockTab = DockWindowTab.NEW_PACKAGES
            viewModel.setDriverPackageSubTab(0)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {

        // ── 1. Primary Feed: DriverPackagesTab (Full screen active packages with infinite scroll) ──
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

        // ── 2. Reused Floating Window Backdrop Scrim ────────────────────────
        AnimatedVisibility(
            visible = activeDockTab != null,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        activeDockTab = null
                        onDismissMenus()
                    }
            )
        }

        // ── 3. The Reused Floating Window (Slides up from dock, not edge-to-edge) ──
        AnimatedVisibility(
            visible = activeDockTab != null,
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)
            ) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            ) { it } + fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 80.dp)
        ) {
            AcrylicGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 540.dp)
                    .fillMaxHeight(0.72f),
                shape = FluidShapes.dock,
                backgroundColor = colors.surface.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, colors.divider.copy(alpha = 0.8f)),
                shadowElevation = 14.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Drag handle pill
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(colors.divider)
                    )

                    Spacer(Modifier.height(10.dp))

                    // ── Dynamic Window Content ──────────────────────────────
                    Box(modifier = Modifier.weight(1f)) {
                        when (activeDockTab) {
                            DockWindowTab.NEW_PACKAGES -> {
                                NewPackagesSheetContent(
                                    incomingItems = incomingItems,
                                    hasVehicle = state.driverHasVehicle,
                                    hasActiveTrip = state.hasActiveTrip,
                                    isLoading = state.isDriverInitialLoading,
                                    isAcceptingTransfer = state.isAcceptingTransfer,
                                    acceptTransferPackageId = state.acceptTransferPackageId,
                                    pickingUpPackageId = state.pickingUpPackageId,
                                    newSubscriptionPkgId = state.newPackageFromSubscription?.id,
                                    viewModel = viewModel
                                )
                            }
                            DockWindowTab.TRIP -> {
                                TripSheetContent(
                                    activeTrip = activeTrip,
                                    hasActiveTrip = hasActiveTrip,
                                    vehicle = vehicle,
                                    viewModel = viewModel
                                )
                            }
                            DockWindowTab.NOTIFICATIONS -> {
                                NotificationsSheetContent(
                                    notices = state.notices,
                                    onNoticeClick = { notice ->
                                        notice.resourceId?.let { pkgId ->
                                            viewModel.openPackageDetail(pkgId)
                                        }
                                    }
                                )
                            }
                            DockWindowTab.PROFILE -> {
                                ProfileSheetContent(
                                    profile = profile,
                                    themeMode = themeMode,
                                    isPipEnabled = isPipEnabled,
                                    keepScreenAwake = keepScreenAwake,
                                    completedTrips = completedTrips,
                                    onThemeModeChange = onThemeModeChange,
                                    onPipEnabledChange = onPipEnabledChange,
                                    onKeepScreenAwakeChange = onKeepScreenAwakeChange,
                                    onCompletedTripsClick = onCompletedTripsClick,
                                    onLogout = onLogout
                                )
                            }
                            DockWindowTab.SEARCH -> {
                                SearchSheetContent(
                                    viewModel = viewModel,
                                    searchQuery = state.driverPackageSearchQuery
                                )
                            }
                            null -> {}
                        }
                    }
                }
            }
        }

        // ── 4. Floating Action and Bottom Grassy Dock Bar ────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            // 4a. Floating Add Button above dock on the right side
            AnimatedVisibility(
                visible = activeDockTab == null,
                enter = fadeIn(spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.75f)) +
                        scaleIn(spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.75f)),
                exit = fadeOut(tween(150)) + scaleOut(tween(150))
            ) {
                Surface(
                    modifier = Modifier
                        .padding(end = 20.dp, bottom = 10.dp)
                        .size(52.dp)
                        .fluidBounceClick(scaleDown = 0.90f) {
                            if (hasActiveTrip) {
                                onCreatePackage()
                            } else {
                                showAddChoiceDialog = true
                            }
                        },
                    shape = CircleShape,
                    color = colors.purple,
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Add Package or Trip",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // 4b. Floating Grassy Dock Bar
            GrassyDock(
                activeTab = activeDockTab,
                onTabClick = { tab ->
                    activeDockTab = if (activeDockTab == tab) null else tab
                },
                profile = profile,
                incomingCount = incomingItems.size,
                unreadNoticesCount = unreadNoticeCount,
                hasActiveTrip = hasActiveTrip,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        // ── 5. Add Choice Dialog (Package vs Trip when no active trip) ────────
        if (showAddChoiceDialog) {
            AlertDialog(
                onDismissRequest = { showAddChoiceDialog = false },
                title = { Text("Create", fontWeight = FontWeight.Bold, color = colors.textPrimary) },
                text = { Text("What would you like to create?", color = colors.textSecondary) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showAddChoiceDialog = false
                            onCreatePackage()
                        }
                    ) {
                        Text("New Package", fontWeight = FontWeight.Bold, color = colors.purple)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showAddChoiceDialog = false
                            viewModel.openDriverCreateTrip()
                        }
                    ) {
                        Text("Start Trip", fontWeight = FontWeight.Bold, color = colors.purple)
                    }
                },
                shape = FluidShapes.card,
                containerColor = colors.surface
            )
        }

        // ── 6. Floating New-Package Toast (Swipeable) ────────────────────────
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
                            .clickable {
                                viewModel.onNewPackageToastClicked()
                                activeDockTab = DockWindowTab.NEW_PACKAGES
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surface,
                        tonalElevation = 8.dp,
                        shadowElevation = 12.dp,
                        border = BorderStroke(1.5.dp, colors.blue.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colors.green.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.LocalShipping, null, tint = colors.green, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("New package available!", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(newPkg.id, color = colors.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${newPkg.fromAddress} → ${newPkg.toAddress}", color = colors.textSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.ChevronRight, null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                LaunchedEffect(newPkg.id) {
                    delay(8_000)
                    viewModel.dismissNewPackageToast()
                }
            }
        }

        // ── 7. Shared Package Dialogs ────────────────────────────────────────
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
                onPickup = { viewModel.pickupPackage(selectedPkg.id) },
                onCancelTransfer = { pkgId, tId -> viewModel.cancelTransferOffer(pkgId, tId) },
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

// ── Grassy Floating Dock Component ──────────────────────────────────────────

@Composable
fun GrassyDock(
    activeTab: DockWindowTab?,
    onTabClick: (DockWindowTab) -> Unit,
    profile: DriverProfile,
    incomingCount: Int,
    unreadNoticesCount: Int,
    hasActiveTrip: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalDriversColors.current

    AcrylicGlassSurface(
        shape = FluidShapes.dock,
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
        backgroundColor = colors.surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, colors.divider.copy(alpha = 0.7f)),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Profile Avatar
            val isProfileActive = activeTab == DockWindowTab.PROFILE
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isProfileActive) colors.purple.copy(alpha = 0.18f) else colors.surfaceAlt)
                    .border(
                        if (isProfileActive) BorderStroke(2.dp, colors.purple)
                        else BorderStroke(1.dp, colors.divider.copy(alpha = 0.5f)),
                        shape = CircleShape
                    )
                    .fluidBounceClick(scaleDown = 0.90f) { onTabClick(DockWindowTab.PROFILE) },
                contentAlignment = Alignment.Center
            ) {
                val avatarUrl = profile.avatarUrl
                if (!avatarUrl.isNullOrBlank()) {
                    CachedAvatarImage(remoteUrl = avatarUrl, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text(
                        profile.name.firstOrNull()?.uppercase() ?: "D",
                        color = if (isProfileActive) colors.purple else colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            // 2. Search Icon Button
            DockIconButton(
                icon = Icons.Filled.Search,
                contentDescription = "Search",
                isActive = activeTab == DockWindowTab.SEARCH,
                onClick = { onTabClick(DockWindowTab.SEARCH) }
            )

            // 3. New Packages Icon Button (with badge)
            DockIconButton(
                icon = Icons.Filled.MoveToInbox,
                contentDescription = "New Packages",
                isActive = activeTab == DockWindowTab.NEW_PACKAGES,
                badgeCount = incomingCount,
                onClick = { onTabClick(DockWindowTab.NEW_PACKAGES) }
            )

            // 4. Trips Icon Button (with live dot)
            DockIconButton(
                icon = Icons.Filled.DirectionsCar,
                contentDescription = "Trips",
                isActive = activeTab == DockWindowTab.TRIP,
                hasLiveDot = hasActiveTrip,
                onClick = { onTabClick(DockWindowTab.TRIP) }
            )

            // 5. Notifications Icon Button (with badge)
            DockIconButton(
                icon = Icons.Filled.Notifications,
                contentDescription = "Notifications",
                isActive = activeTab == DockWindowTab.NOTIFICATIONS,
                badgeCount = unreadNoticesCount,
                onClick = { onTabClick(DockWindowTab.NOTIFICATIONS) }
            )
        }
    }
}

@Composable
private fun DockIconButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    badgeCount: Int = 0,
    hasLiveDot: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalDriversColors.current
    val tint by animateColorAsState(
        if (isActive) colors.purple else colors.textSecondary,
        tween(200),
        label = "dockIconTint"
    )
    val bgPill by animateColorAsState(
        if (isActive) colors.purple.copy(alpha = 0.12f) else Color.Transparent,
        tween(200),
        label = "dockIconBg"
    )

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(FluidShapes.pill)
            .background(bgPill)
            .fluidBounceClick(scaleDown = 0.90f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BadgedBox(
            badge = {
                if (badgeCount > 0) {
                    Badge(containerColor = colors.purple, contentColor = Color.White) {
                        Text("$badgeCount", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (hasLiveDot) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(CavgoGreen)
                    )
                }
            }
        ) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

// ── Unified Sheet Contents ──────────────────────────────────────────────────

@Composable
private fun NewPackagesSheetContent(
    incomingItems: List<IncomingPackageItem>,
    hasVehicle: Boolean,
    hasActiveTrip: Boolean,
    isLoading: Boolean,
    isAcceptingTransfer: Boolean,
    acceptTransferPackageId: String?,
    pickingUpPackageId: String?,
    newSubscriptionPkgId: String?,
    viewModel: TripViewModel
) {
    if (!hasVehicle) {
        EmptyPackagesState(
            title = "Vehicle Required",
            subtitle = "You must have an assigned vehicle to access incoming packages"
        )
    } else if (isLoading && incomingItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = LocalDriversColors.current.purple)
        }
    } else if (incomingItems.isEmpty()) {
        EmptyPackagesState(
            title = "No new packages",
            subtitle = "Incoming offers, transfers, and office assignments will appear here"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!hasActiveTrip) {
                item {
                    val colors = LocalDriversColors.current
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = colors.amber.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, colors.amber.copy(alpha = 0.4f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, null, tint = colors.amber, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("No active trip — start a trip to claim open offers", fontSize = 12.sp, color = colors.textPrimary)
                        }
                    }
                }
            }
            items(incomingItems, key = {
                when (it) {
                    is IncomingPackageItem.Assigned -> "assigned-${it.pkg.id}"
                    is IncomingPackageItem.Transfer -> "transfer-${it.transferId}-${it.pkg.id}"
                    is IncomingPackageItem.Offer -> "offer-${it.transferId}-${it.pkg.id}"
                }
            }) { item ->
                val pkg = item.pkg
                val acceptAction: () -> Unit = when (item) {
                    is IncomingPackageItem.Assigned -> { { viewModel.pickupPackage(pkg.id) } }
                    is IncomingPackageItem.Transfer -> {
                        when (item.ruleType) {
                            "SECURE" -> { { viewModel.openAcceptTransferCodeDialog(pkg.id, item.transferId, "SECURE") } }
                            "CONFIRM" -> { { viewModel.openRequestTransferDialog(pkg.id, item.transferId) } }
                            else -> { { viewModel.acceptIncomingTransfer(item.transferId, pkg.id) } }
                        }
                    }
                    is IncomingPackageItem.Offer -> {
                        when (item.ruleType) {
                            "SECURE" -> { { viewModel.openAcceptTransferCodeDialog(pkg.id, item.transferId, "SECURE") } }
                            "CONFIRM" -> { { viewModel.openRequestTransferDialog(pkg.id, item.transferId) } }
                            else -> { { viewModel.acceptAUTOTransfer(pkg.id, item.transferId) } }
                        }
                    }
                }
                val isThisAccepting = (isAcceptingTransfer && (acceptTransferPackageId == pkg.id || acceptTransferPackageId == null)) ||
                        (pickingUpPackageId == pkg.id)

                DriverIncomingPackageCard(
                    item = item,
                    onAccept = acceptAction,
                    onDetail = { viewModel.openPackageDetail(pkg.id) },
                    isAccepting = isThisAccepting,
                    hasActiveTrip = hasActiveTrip,
                    isNew = pkg.id == newSubscriptionPkgId
                )
            }
        }
    }
}

@Composable
private fun TripSheetContent(
    activeTrip: BackendStorage.DriverTrip?,
    hasActiveTrip: Boolean,
    vehicle: DriverVehicle?,
    viewModel: TripViewModel
) {
    val colors = LocalDriversColors.current

    val plateNumber = vehicle?.plateNumber?.takeIf { it.isNotBlank() } ?: activeTrip?.vehicleLicensePlate
    val seatsCount = vehicle?.seats ?: activeTrip?.seats
    val vehicleModel = vehicle?.model?.takeIf { it.isNotBlank() } ?: activeTrip?.vehicleModel
    val vehicleInfoAvailable = !plateNumber.isNullOrBlank() || seatsCount != null

    if (hasActiveTrip && activeTrip != null) {
        val routeLabel = activeTrip.routeName
            ?: (if (activeTrip.origin != null && activeTrip.destination != null) "${activeTrip.origin} → ${activeTrip.destination}" else "Active Trip")
        val nextWp = activeTrip.waypoints.firstOrNull { it.isNext } ?: activeTrip.waypoints.firstOrNull { !it.isPassed }
        val remainingDist = nextWp?.remainingDistance?.takeIf { it > 0.0 }
            ?: activeTrip.remainingDistanceToDestination?.takeIf { it > 0.0 }
        val distText = remainingDist?.let { d ->
            if (d < 1000) "${d.toInt()} m" else "${String.format(Locale.US, "%.1f", d / 1000.0)} km"
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Vehicle plate and seats just above trip data
            if (vehicleInfoAvailable) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = FluidShapes.card,
                        color = colors.surfaceAlt,
                        border = BorderStroke(1.dp, colors.divider.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colors.purple.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.DirectionsCar,
                                    contentDescription = null,
                                    tint = colors.purple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    plateNumber ?: "Vehicle",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = colors.textPrimary
                                )
                                val subtitle = buildString {
                                    if (!vehicleModel.isNullOrBlank()) append(vehicleModel)
                                    if (seatsCount != null) {
                                        if (isNotEmpty()) append(" • ")
                                        append("$seatsCount seats")
                                    }
                                }
                                if (subtitle.isNotEmpty()) {
                                    Text(subtitle, fontSize = 12.sp, color = colors.textSecondary)
                                }
                            }
                        }
                    }
                }
            }

            // Live Trip Card
            item {
                AcrylicGlassSurface(
                    shape = FluidShapes.card,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(CavgoGreen))
                            Spacer(Modifier.width(8.dp))
                            Text("LIVE TRIP IN PROGRESS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CavgoGreen)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(routeLabel, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        if (distText != null) {
                            Spacer(Modifier.height(4.dp))
                            Text("Next Stop: ${nextWp?.locationName ?: "Upcoming"} ($distText)", fontSize = 13.sp, color = colors.purple)
                        }
                    }
                }
            }

            if (activeTrip.waypoints.isNotEmpty()) {
                item {
                    Text("ROUTE STOPS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
                }

                items(activeTrip.waypoints) { wp ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = FluidShapes.card,
                        color = colors.surfaceAlt,
                        border = BorderStroke(1.dp, if (wp.isNext) colors.purple.copy(alpha = 0.5f) else colors.divider.copy(alpha = 0.4f))
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(24.dp).clip(CircleShape).background(
                                    if (wp.isPassed) colors.textSecondary.copy(alpha = 0.2f)
                                    else if (wp.isNext) colors.purple.copy(alpha = 0.2f)
                                    else colors.surface
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (wp.isPassed) Icons.Filled.Check else Icons.Filled.Place,
                                    null,
                                    tint = if (wp.isPassed) colors.textSecondary else if (wp.isNext) colors.purple else colors.textPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    wp.locationName ?: "Stop",
                                    fontWeight = if (wp.isNext) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp,
                                    color = if (wp.isPassed) colors.textSecondary else colors.textPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // No active trip
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Vehicle plate and seats just above trip data
            if (vehicleInfoAvailable) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = FluidShapes.card,
                        color = colors.surfaceAlt,
                        border = BorderStroke(1.dp, colors.divider.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colors.purple.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.DirectionsCar,
                                    contentDescription = null,
                                    tint = colors.purple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    plateNumber ?: "Vehicle",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = colors.textPrimary
                                )
                                val subtitle = buildString {
                                    if (!vehicleModel.isNullOrBlank()) append(vehicleModel)
                                    if (seatsCount != null) {
                                        if (isNotEmpty()) append(" • ")
                                        append("$seatsCount seats")
                                    }
                                }
                                if (subtitle.isNotEmpty()) {
                                    Text(subtitle, fontSize = 12.sp, color = colors.textSecondary)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.DirectionsCar, null, tint = colors.textSecondary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("No active trip", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("Start a trip to begin accepting and delivering packages", fontSize = 13.sp, color = colors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { viewModel.openDriverCreateTrip() },
                        shape = FluidShapes.pill,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.purple),
                        modifier = Modifier.height(44.dp).padding(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Start New Trip", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsSheetContent(
    notices: List<Notice>,
    onNoticeClick: (Notice) -> Unit
) {
    val colors = LocalDriversColors.current

    if (notices.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.NotificationsNone, null, tint = colors.textSecondary, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(10.dp))
                Text("No notifications", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text("You're all caught up!", fontSize = 13.sp, color = colors.textSecondary)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(notices, key = { it.id }) { notice ->
                val isUnread = notice.viewerReadAt == null
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNoticeClick(notice) },
                    shape = FluidShapes.card,
                    color = if (isUnread) colors.purple.copy(alpha = 0.08f) else colors.surface,
                    border = BorderStroke(1.dp, if (isUnread) colors.purple.copy(alpha = 0.3f) else colors.divider.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isUnread) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(colors.purple))
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(notice.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            Spacer(Modifier.height(2.dp))
                            Text(notice.message, fontSize = 12.sp, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSheetContent(
    profile: DriverProfile,
    themeMode: AppThemeMode,
    isPipEnabled: Boolean,
    keepScreenAwake: Boolean,
    completedTrips: List<CompletedTrip> = emptyList(),
    onThemeModeChange: (AppThemeMode) -> Unit,
    onPipEnabledChange: (Boolean) -> Unit,
    onKeepScreenAwakeChange: (Boolean) -> Unit,
    onCompletedTripsClick: () -> Unit = {},
    onLogout: () -> Unit
) {
    val colors = LocalDriversColors.current
    var isThemeDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Row 1: Theme Dropdown + Keep Awake ────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Theme Switcher Tile (with Dropdown)
            Box(modifier = Modifier.weight(1f)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .fluidBounceClick(scaleDown = 0.93f) { isThemeDropdownExpanded = true },
                    shape = FluidShapes.card,
                    color = colors.surfaceAlt,
                    border = BorderStroke(1.dp, colors.divider.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(colors.purple.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "Theme",
                                tint = colors.purple,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Theme",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                maxLines = 1
                            )
                            Text(
                                when (themeMode) {
                                    AppThemeMode.SYSTEM -> "System"
                                    AppThemeMode.DARK -> "Dark"
                                    AppThemeMode.LIGHT -> "Light"
                                },
                                fontSize = 11.sp,
                                color = colors.purple,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = isThemeDropdownExpanded,
                    onDismissRequest = { isThemeDropdownExpanded = false },
                    modifier = Modifier.background(colors.surface),
                    shape = FluidShapes.card
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "System",
                                color = if (themeMode == AppThemeMode.SYSTEM) colors.purple else colors.textPrimary,
                                fontWeight = if (themeMode == AppThemeMode.SYSTEM) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        trailingIcon = {
                            if (themeMode == AppThemeMode.SYSTEM) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = colors.purple, modifier = Modifier.size(16.dp))
                            }
                        },
                        onClick = {
                            onThemeModeChange(AppThemeMode.SYSTEM)
                            isThemeDropdownExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Light",
                                color = if (themeMode == AppThemeMode.LIGHT) colors.purple else colors.textPrimary,
                                fontWeight = if (themeMode == AppThemeMode.LIGHT) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        trailingIcon = {
                            if (themeMode == AppThemeMode.LIGHT) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = colors.purple, modifier = Modifier.size(16.dp))
                            }
                        },
                        onClick = {
                            onThemeModeChange(AppThemeMode.LIGHT)
                            isThemeDropdownExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Dark",
                                color = if (themeMode == AppThemeMode.DARK) colors.purple else colors.textPrimary,
                                fontWeight = if (themeMode == AppThemeMode.DARK) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        trailingIcon = {
                            if (themeMode == AppThemeMode.DARK) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = colors.purple, modifier = Modifier.size(16.dp))
                            }
                        },
                        onClick = {
                            onThemeModeChange(AppThemeMode.DARK)
                            isThemeDropdownExpanded = false
                        }
                    )
                }
            }

            // Keep Screen Awake Tile
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .fluidBounceClick(scaleDown = 0.93f) {
                        onKeepScreenAwakeChange(!keepScreenAwake)
                    },
                shape = FluidShapes.card,
                color = if (keepScreenAwake) colors.purple.copy(alpha = 0.10f) else colors.surfaceAlt,
                border = BorderStroke(
                    1.dp,
                    if (keepScreenAwake) colors.purple.copy(alpha = 0.4f) else colors.divider.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (keepScreenAwake) colors.purple.copy(alpha = 0.18f)
                                else colors.textSecondary.copy(alpha = 0.12f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.ScreenLockLandscape,
                            contentDescription = "Keep Screen Awake",
                            tint = if (keepScreenAwake) colors.purple else colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Keep Awake",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1
                        )
                        Text(
                            if (keepScreenAwake) "Always on" else "Off",
                            fontSize = 11.sp,
                            color = if (keepScreenAwake) colors.purple else colors.textSecondary,
                            maxLines = 1
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (keepScreenAwake) CavgoGreen else colors.textSecondary.copy(alpha = 0.3f))
                    )
                }
            }
        }

        // ── Row 2: Picture-in-Picture + Trip History ─────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Picture-in-Picture Tile
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .fluidBounceClick(scaleDown = 0.93f) {
                        onPipEnabledChange(!isPipEnabled)
                    },
                shape = FluidShapes.card,
                color = if (isPipEnabled) colors.purple.copy(alpha = 0.10f) else colors.surfaceAlt,
                border = BorderStroke(
                    1.dp,
                    if (isPipEnabled) colors.purple.copy(alpha = 0.4f) else colors.divider.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPipEnabled) colors.purple.copy(alpha = 0.18f)
                                else colors.textSecondary.copy(alpha = 0.12f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PictureInPicture,
                            contentDescription = "Picture-in-Picture",
                            tint = if (isPipEnabled) colors.purple else colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "PiP Window",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1
                        )
                        Text(
                            if (isPipEnabled) "Enabled" else "Off",
                            fontSize = 11.sp,
                            color = if (isPipEnabled) colors.purple else colors.textSecondary,
                            maxLines = 1
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isPipEnabled) CavgoGreen else colors.textSecondary.copy(alpha = 0.3f))
                    )
                }
            }

            // Trip History Tile
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .fluidBounceClick(scaleDown = 0.93f) {
                        onCompletedTripsClick()
                    },
                shape = FluidShapes.card,
                color = colors.surfaceAlt,
                border = BorderStroke(1.dp, colors.divider.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colors.purple.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = "Trip History",
                            tint = colors.purple,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Trip History",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1
                        )
                        Text(
                            "${completedTrips.size} trips",
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                            maxLines = 1
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f, fill = false))

        // ── Row 3: Equal Width Driver Profile Button + Logout Button ─────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Driver Profile (Avatar + Name only, equal width)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = FluidShapes.card,
                color = colors.surfaceAlt,
                border = BorderStroke(1.dp, colors.divider.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val avatarUrl = profile.avatarUrl
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(colors.purple.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!avatarUrl.isNullOrBlank()) {
                            CachedAvatarImage(remoteUrl = avatarUrl, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Text(
                                profile.name.firstOrNull()?.uppercase() ?: "D",
                                color = colors.purple,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        profile.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Logout Button (Equal width with driver profile)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .fluidBounceClick(scaleDown = 0.93f, onClick = onLogout),
                shape = FluidShapes.card,
                color = colors.red.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, colors.red.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Log Out",
                        tint = colors.red,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Log Out",
                        color = colors.red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSheetContent(
    viewModel: TripViewModel,
    searchQuery: String
) {
    val colors = LocalDriversColors.current
    val state by viewModel.state.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Automatically request focus and launch keyboard when search opens
    LaunchedEffect(Unit) {
        delay(120)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val query = searchQuery.trim()
    val suggestions = remember(query, state.driverCurrentPackages) {
        if (query.isBlank()) {
            emptyList()
        } else {
            state.driverCurrentPackages.filter { pkg ->
                pkg.id.contains(query, ignoreCase = true) ||
                pkg.trackingCode.contains(query, ignoreCase = true) ||
                pkg.recipientName.contains(query, ignoreCase = true) ||
                pkg.toAddress.contains(query, ignoreCase = true) ||
                pkg.fromAddress.contains(query, ignoreCase = true) ||
                pkg.description.contains(query, ignoreCase = true) ||
                pkg.recipientPhone.contains(query, ignoreCase = true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateDriverPackageSearch(it) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .focusRequester(focusRequester),
            placeholder = { Text("Search package ID, recipient, stop...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = colors.purple, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { viewModel.updateDriverPackageSearch("") }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Filled.Close, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
            shape = FluidShapes.pill,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = colors.surfaceAlt,
                focusedContainerColor = colors.surface,
                unfocusedBorderColor = colors.divider.copy(alpha = 0.6f),
                focusedBorderColor = colors.purple,
                cursorColor = colors.purple
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
        )

        Spacer(Modifier.height(14.dp))

        if (query.isBlank()) {
            // Initial empty search state (no package list, just clean suggestions / hints)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(colors.purple.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = colors.purple,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Search Packages",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Type to find tracking code, recipient or stop",
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )

                // Quick route stop chips if on an active trip
                val stops = remember(state.activeDriverTrip) {
                    state.activeDriverTrip?.waypoints?.mapNotNull { it.locationName?.takeIf { s -> s.isNotBlank() } }?.take(3) ?: emptyList()
                }
                if (stops.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        stops.forEach { stopName ->
                            Surface(
                                shape = FluidShapes.pill,
                                color = colors.surfaceAlt,
                                border = BorderStroke(1.dp, colors.divider.copy(alpha = 0.5f)),
                                modifier = Modifier.fluidBounceClick(scaleDown = 0.92f) {
                                    viewModel.updateDriverPackageSearch(stopName)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Place, null, tint = colors.purple, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stopName, fontSize = 11.sp, color = colors.textPrimary, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        } else if (suggestions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 32.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.SearchOff, null, tint = colors.textSecondary, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No packages match \"$query\"", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("Check tracking code or recipient name", fontSize = 12.sp, color = colors.textSecondary)
                }
            }
        } else {
            // Live suggestions as the user types
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(suggestions, key = { it.id }) { pkg ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fluidBounceClick(scaleDown = 0.98f) {
                                keyboardController?.hide()
                                viewModel.openPackageDetail(pkg.id)
                            },
                        shape = FluidShapes.card,
                        color = colors.surfaceAlt,
                        border = BorderStroke(1.dp, colors.divider.copy(alpha = 0.5f))
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colors.purple.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Inventory2,
                                    contentDescription = null,
                                    tint = colors.purple,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    pkg.trackingCode.ifBlank { pkg.id },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = colors.textPrimary
                                )
                                Spacer(Modifier.height(2.dp))
                                val recipientText = pkg.recipientName.ifBlank { "Recipient" }
                                Text(
                                    "$recipientText • ${pkg.toAddress}",
                                    fontSize = 11.sp,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
