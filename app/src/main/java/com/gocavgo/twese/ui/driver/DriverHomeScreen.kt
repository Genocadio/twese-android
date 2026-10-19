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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.gocavgo.twese.data.TripStop
import com.gocavgo.twese.data.Package
import kotlinx.coroutines.launch
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
    LaunchedEffect(state.showNewPackageToast) {
        if (state.showNewPackageToast) {
            activeDockTab = DockWindowTab.NEW_PACKAGES
            viewModel.dismissNewPackageToast()
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
                                        if (notice.resourceId.isNotBlank()) {
                                            viewModel.openPackageDetail(notice.resourceId)
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
    val state by viewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val plateNumber = vehicle?.plateNumber?.takeIf { it.isNotBlank() } ?: activeTrip?.vehicleLicensePlate
    val totalSeats = activeTrip?.seats ?: vehicle?.seats
    val remainingSeats = activeTrip?.remainingSeats ?: totalSeats
    val speedKmh = activeTrip?.currentSpeed?.toFloat() ?: state.driverLocation.speedKmh
    val vehicleInfoAvailable = !plateNumber.isNullOrBlank() || totalSeats != null

    if (hasActiveTrip && activeTrip != null) {
        val isScheduled = activeTrip.status.equals("SCHEDULED", ignoreCase = true)
        val isCompleted = activeTrip.status.equals("COMPLETED", ignoreCase = true)
        val isDeparted = activeTrip.status.equals("IN_PROGRESS", ignoreCase = true)

        val originName = activeTrip.origin ?: state.trip.stops.firstOrNull()?.name ?: "Origin"
        val destName = activeTrip.destination ?: state.trip.stops.lastOrNull()?.name ?: "Destination"

        // Build complete list of stops
        val stops = remember(state.trip.stops, activeTrip) {
            if (state.trip.stops.isNotEmpty()) {
                state.trip.stops
            } else {
                val list = mutableListOf<TripStop>()
                list.add(
                    TripStop(
                        id = 0,
                        name = originName,
                        address = originName,
                        lat = activeTrip.originLatitude ?: 0.0,
                        lng = activeTrip.originLongitude ?: 0.0,
                        pickups = emptyList(),
                        dropoffs = emptyList()
                    )
                )
                activeTrip.waypoints.forEachIndexed { i, wp ->
                    val wName = wp.locationName ?: "Stop ${i + 1}"
                    list.add(
                        TripStop(
                            id = i + 1,
                            name = wName,
                            address = wName,
                            lat = wp.latitude,
                            lng = wp.longitude,
                            pickups = emptyList(),
                            dropoffs = emptyList()
                        )
                    )
                }
                list.add(
                    TripStop(
                        id = list.size,
                        name = destName,
                        address = destName,
                        lat = activeTrip.destinationLatitude ?: 0.0,
                        lng = activeTrip.destinationLongitude ?: 0.0,
                        pickups = emptyList(),
                        dropoffs = emptyList()
                    )
                )
                list
            }
        }

        // Active target stop index
        val activeWp = activeTrip.waypoints.firstOrNull { it.isNext }
            ?: activeTrip.waypoints.firstOrNull { !it.isPassed }
            ?: activeTrip.waypoints.getOrNull(state.currentStopIndex)

        val activeStopIndex = remember(activeTrip, stops, state.currentStopIndex) {
            when {
                isCompleted -> (stops.size - 1).coerceAtLeast(0)
                isScheduled -> 0
                activeWp != null -> {
                    val wpIdx = activeTrip.waypoints.indexOf(activeWp)
                    if (stops.size > activeTrip.waypoints.size) (wpIdx + 1).coerceIn(0, stops.size - 1)
                    else wpIdx.coerceIn(0, stops.size - 1)
                }
                else -> state.currentStopIndex.coerceIn(0, (stops.size - 1).coerceAtLeast(0))
            }
        }

        val pagerState = rememberPagerState(
            initialPage = activeStopIndex.coerceIn(0, (stops.size - 1).coerceAtLeast(0))
        ) { stops.size }

        // Keep pager synced when active stop updates
        LaunchedEffect(activeStopIndex) {
            if (activeStopIndex in stops.indices) {
                pagerState.animateScrollToPage(activeStopIndex)
            }
        }

        // Total distance to destination
        val distToDestMeters = activeTrip.remainingDistanceToDestination?.takeIf { it > 0.0 }
        val distToDestStr = distToDestMeters?.let { d ->
            if (d < 1000) "${d.toInt()} m" else "${String.format(Locale.US, "%.1f", d / 1000.0)} km"
        }

        // Pulsing animation for live status dot
        val infiniteTransition = rememberInfiniteTransition(label = "liveTripPulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                // Unified Card: Car Data on Top + Trip Data + Swipeable Stops
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = FluidShapes.card,
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.divider.copy(alpha = 0.6f)),
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {

                        // ── 1. CAR DATA ON TOP (Small, compact: Plate, Live Seats, Speed) ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(FluidShapes.pill)
                                .background(colors.surfaceAlt)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Plate
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.DirectionsCar,
                                    contentDescription = null,
                                    tint = colors.purple,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    plateNumber ?: "No Plate",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }

                            // Seats (live remaining seats as they get booked)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.EventSeat,
                                    contentDescription = null,
                                    tint = colors.purple,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                val seatsLabel = if (remainingSeats != null && totalSeats != null) {
                                    "$remainingSeats/$totalSeats seats"
                                } else if (totalSeats != null) {
                                    "$totalSeats seats"
                                } else "—"
                                Text(
                                    seatsLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textSecondary
                                )
                            }

                            // Speed
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Speed,
                                    contentDescription = null,
                                    tint = if (speedKmh >= 1f) colors.purple else colors.textSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "${speedKmh.toInt()} km/h",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (speedKmh >= 1f) colors.purple else colors.textSecondary
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // ── 2. TRIP ROUTE HEADER (Origin ➔ Destination, Live Badge, Remaining Distance) ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Live status pill
                            Surface(
                                shape = FluidShapes.capsule,
                                color = if (isDeparted) CavgoGreen.copy(alpha = 0.12f)
                                else if (isScheduled) colors.amber.copy(alpha = 0.12f)
                                else colors.purple.copy(alpha = 0.12f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isDeparted) CavgoGreen.copy(alpha = 0.35f)
                                    else if (isScheduled) colors.amber.copy(alpha = 0.35f)
                                    else colors.purple.copy(alpha = 0.35f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isDeparted) CavgoGreen.copy(alpha = pulseAlpha)
                                                else if (isScheduled) colors.amber
                                                else colors.purple
                                            )
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        when {
                                            isDeparted -> "LIVE TRIP"
                                            isScheduled -> "SCHEDULED"
                                            isCompleted -> "COMPLETED"
                                            else -> "ACTIVE TRIP"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDeparted) CavgoGreen else if (isScheduled) colors.amber else colors.purple,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            // Total remaining distance to destination
                            if (!distToDestStr.isNullOrBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.NearMe,
                                        contentDescription = null,
                                        tint = colors.purple,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        distToDestStr,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.purple
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Origin ➔ Destination Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(colors.purple))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                originName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = colors.purple,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(colors.red))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                destName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = colors.divider.copy(alpha = 0.5f))
                        Spacer(Modifier.height(10.dp))

                        // ── 3. SWIPEABLE STOPS PAGER (Swipe left/right between stops) ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = {
                                    if (pagerState.currentPage > 0) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                    }
                                },
                                enabled = pagerState.currentPage > 0,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous stop",
                                    tint = if (pagerState.currentPage > 0) colors.purple else colors.divider,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Stop ${pagerState.currentPage + 1} of ${stops.size}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textSecondary
                                )
                                Spacer(Modifier.width(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    stops.indices.forEach { index ->
                                        val isCurrentDot = index == pagerState.currentPage
                                        val dotColor = when {
                                            isCurrentDot -> colors.purple
                                            index < activeStopIndex -> colors.textSecondary.copy(alpha = 0.4f)
                                            else -> colors.divider
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(if (isCurrentDot) 7.dp else 5.dp)
                                                .clip(CircleShape)
                                                .background(dotColor)
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (pagerState.currentPage < stops.size - 1) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    }
                                },
                                enabled = pagerState.currentPage < stops.size - 1,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next stop",
                                    tint = if (pagerState.currentPage < stops.size - 1) colors.purple else colors.divider,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth()
                        ) { page ->
                            val stop = stops.getOrNull(page) ?: return@HorizontalPager
                            val stopName = stop.name

                            val isStopPassed = when {
                                isCompleted -> true
                                isScheduled -> false
                                else -> {
                                    if (page == 0) true
                                    else if (page == stops.size - 1) false
                                    else activeTrip.waypoints.getOrNull(page - 1)?.isPassed ?: (page < activeStopIndex)
                                }
                            }
                            val isStopCurrent = !isCompleted && page == activeStopIndex

                            // Stop distance & time
                            val wpForPage = if (page == 0 || page == stops.size - 1) null
                            else activeTrip.waypoints.getOrNull(page - 1)

                            val stopDistMeters = if (isStopCurrent) {
                                activeWp?.remainingDistance?.takeIf { it > 0.0 }
                                    ?: activeTrip.remainingDistanceToDestination?.takeIf { it > 0.0 }
                            } else wpForPage?.remainingDistance?.takeIf { it > 0.0 }

                            val stopDistStr = stopDistMeters?.let { d ->
                                if (d < 1000) "${d.toInt()} m" else "${String.format(Locale.US, "%.1f", d / 1000.0)} km"
                            }

                            val stopTimeSecs = if (isStopCurrent) {
                                activeWp?.remainingTime?.takeIf { it > 0.0 }
                                    ?: activeTrip.remainingTimeToDestination?.takeIf { it > 0.0 }
                            } else wpForPage?.remainingTime?.takeIf { it > 0.0 }

                            val stopTimeStr = stopTimeSecs?.let { s ->
                                val mins = (s / 60.0).toInt().coerceAtLeast(1)
                                if (mins < 60) "$mins min"
                                else "${mins / 60}h ${mins % 60}m"
                            }

                            // Match pickups & dropoffs at this stop
                            val pickupsAtStop = remember(state.driverCurrentPackages, stop) {
                                if (stop.pickups.isNotEmpty()) stop.pickups
                                else {
                                    state.driverCurrentPackages
                                        .filter {
                                            it.fromAddress.isNotBlank() &&
                                            (it.fromAddress.contains(stopName, true) || stopName.contains(it.fromAddress, true))
                                        }
                                        .map { pkg ->
                                            Package(id = pkg.id, label = pkg.description.ifBlank { pkg.id }, recipient = pkg.recipientName, weight = pkg.weight)
                                        }
                                }
                            }

                            val dropoffsAtStop = remember(state.driverCurrentPackages, stop) {
                                if (stop.dropoffs.isNotEmpty()) stop.dropoffs
                                else {
                                    state.driverCurrentPackages
                                        .filter {
                                            it.toAddress.isNotBlank() &&
                                            (it.toAddress.contains(stopName, true) || stopName.contains(it.toAddress, true))
                                        }
                                        .map { pkg ->
                                            Package(id = pkg.id, label = pkg.description.ifBlank { pkg.id }, recipient = pkg.recipientName, weight = pkg.weight)
                                        }
                                }
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = FluidShapes.card,
                                color = colors.surfaceAlt,
                                border = BorderStroke(
                                    1.dp,
                                    if (isStopCurrent) colors.purple.copy(alpha = 0.5f)
                                    else if (isStopPassed) colors.divider.copy(alpha = 0.4f)
                                    else colors.divider.copy(alpha = 0.6f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Stop title + status badge + distance/time
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Stop badge
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = when {
                                                isStopPassed -> colors.textSecondary.copy(alpha = 0.12f)
                                                isStopCurrent -> (if (isScheduled) colors.amber else colors.purple).copy(alpha = 0.14f)
                                                else -> colors.divider.copy(alpha = 0.4f)
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (isStopPassed) {
                                                    Icon(Icons.Filled.Check, null, tint = colors.textSecondary, modifier = Modifier.size(12.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("PASSED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
                                                } else if (isStopCurrent) {
                                                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (isScheduled) colors.amber else colors.purple))
                                                    Spacer(Modifier.width(5.dp))
                                                    Text(
                                                        if (isScheduled) "ORIGIN (DEPARTURE)" else "CURRENT STOP",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isScheduled) colors.amber else colors.purple
                                                    )
                                                } else {
                                                    Text("UPCOMING", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
                                                }
                                            }
                                        }

                                        // Stop distance / ETA if available
                                        val etaText = buildString {
                                            if (!stopDistStr.isNullOrBlank()) append(stopDistStr)
                                            if (!stopTimeStr.isNullOrBlank()) {
                                                if (isNotEmpty()) append(" • ")
                                                append(stopTimeStr)
                                            }
                                        }
                                        if (etaText.isNotBlank() && !isStopPassed) {
                                            Text(
                                                etaText,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isStopCurrent) colors.purple else colors.textSecondary
                                            )
                                        } else if (isStopPassed) {
                                            Text(
                                                "Completed",
                                                fontSize = 11.sp,
                                                color = colors.textSecondary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    // Stop Name
                                    Text(
                                        stopName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = if (isStopPassed) colors.textSecondary else colors.textPrimary
                                    )

                                    Spacer(Modifier.height(10.dp))

                                    // ── Pickups and Dropoffs Cards for this stop ──
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Pickups
                                        Surface(
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            color = colors.surface,
                                            border = BorderStroke(1.dp, colors.divider.copy(alpha = 0.5f))
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        if (isStopPassed) Icons.Filled.Check else Icons.Filled.AddCircle,
                                                        null,
                                                        tint = colors.purple,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(Modifier.width(5.dp))
                                                    Text(
                                                        if (isStopPassed) "Picked up (${pickupsAtStop.size})"
                                                        else "To pick up (${pickupsAtStop.size})",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = colors.purple
                                                    )
                                                }
                                                Spacer(Modifier.height(6.dp))
                                                if (pickupsAtStop.isEmpty()) {
                                                    Text("None", fontSize = 11.sp, color = colors.textSecondary)
                                                } else {
                                                    pickupsAtStop.take(3).forEach { pkg ->
                                                        Text(
                                                            "• ${pkg.id}",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = colors.textPrimary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier
                                                                .clickable { viewModel.openPackageDetail(pkg.id) }
                                                                .padding(vertical = 1.dp)
                                                        )
                                                    }
                                                    if (pickupsAtStop.size > 3) {
                                                        Text("+${pickupsAtStop.size - 3} more", fontSize = 10.sp, color = colors.textSecondary)
                                                    }
                                                }
                                            }
                                        }

                                        // Dropoffs
                                        Surface(
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            color = colors.surface,
                                            border = BorderStroke(1.dp, colors.divider.copy(alpha = 0.5f))
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        if (isStopPassed) Icons.Filled.Check else Icons.Filled.RemoveCircle,
                                                        null,
                                                        tint = if (isStopPassed) colors.purple else colors.amber,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(Modifier.width(5.dp))
                                                    Text(
                                                        if (isStopPassed) "Dropped off (${dropoffsAtStop.size})"
                                                        else "To drop off (${dropoffsAtStop.size})",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isStopPassed) colors.purple else colors.amber
                                                    )
                                                }
                                                Spacer(Modifier.height(6.dp))
                                                if (dropoffsAtStop.isEmpty()) {
                                                    Text("None", fontSize = 11.sp, color = colors.textSecondary)
                                                } else {
                                                    dropoffsAtStop.take(3).forEach { pkg ->
                                                        Text(
                                                            "• ${pkg.id}",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = colors.textPrimary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier
                                                                .clickable { viewModel.openPackageDetail(pkg.id) }
                                                                .padding(vertical = 1.dp)
                                                        )
                                                    }
                                                    if (dropoffsAtStop.size > 3) {
                                                        Text("+${dropoffsAtStop.size - 3} more", fontSize = 10.sp, color = colors.textSecondary)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
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
            // Vehicle plate and seats only (compact)
            if (vehicleInfoAvailable) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = FluidShapes.card,
                        color = colors.surfaceAlt,
                        border = BorderStroke(1.dp, colors.divider.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.DirectionsCar,
                                contentDescription = null,
                                tint = colors.purple,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                plateNumber ?: "Vehicle",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = colors.textPrimary
                            )
                            if (totalSeats != null) {
                                Spacer(Modifier.width(10.dp))
                                Text("•", color = colors.textSecondary, fontSize = 12.sp)
                                Spacer(Modifier.width(10.dp))
                                Icon(
                                    Icons.Filled.EventSeat,
                                    contentDescription = null,
                                    tint = colors.purple,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "$totalSeats seats",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                    fontWeight = FontWeight.Medium
                                )
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
                    Text(
                        "Start a trip to begin accepting and delivering packages",
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
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
