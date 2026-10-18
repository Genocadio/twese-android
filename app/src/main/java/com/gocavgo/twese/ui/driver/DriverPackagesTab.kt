package com.gocavgo.twese.ui.driver

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gocavgo.twese.data.ClientPackage
import com.gocavgo.twese.data.PackageStatus
import com.gocavgo.twese.data.hasOpenTransfer
import com.gocavgo.twese.data.sortedByUnreadNotices
import com.gocavgo.twese.ui.common.AcrylicGlassSurface
import com.gocavgo.twese.ui.common.FluidShapes
import com.gocavgo.twese.ui.common.adaptiveHorizontalPadding
import com.gocavgo.twese.ui.common.fluidBounceClick
import com.gocavgo.twese.ui.common.FrostedFloatingIconButton
import com.gocavgo.twese.ui.theme.LocalDriversColors
import com.gocavgo.twese.viewmodel.TripViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverPackagesTab(
    viewModel: TripViewModel,
    onNoticesClick: () -> Unit = {},
    noticeCount: Int = 0
) {
    val state by viewModel.state.collectAsState()
    val colors = LocalDriversColors.current
    LaunchedEffect(Unit) { viewModel.loadDriverPackages() }

    val subTab = state.driverPackageSubTab.coerceIn(0, 1)
    val searchQuery = state.driverPackageSearchQuery
    val isRefreshing = state.isRefreshingPackages
    val isDriverInitLoading = state.isDriverInitialLoading
    val isSelection = state.isSelectionMode
    val selectedIds = state.selectedPackageIds

    val incomingPackageUuids = remember(state.driverIncomingTransfers) {
        state.driverIncomingTransfers.flatMap { it.packages.map { pkg -> pkg.packageUuid } }.toSet()
    }
    val NOT_PICKED_UP_BACKEND_STATUSES = remember { setOf("ORIGIN_OFFICE", "ASSIGNED_DRIVER") }

    // Packages assigned by office but driver hasn't picked up yet
    val assignedToDriverPackages = state.driverCurrentPackages.filter {
        it.backendStatus in NOT_PICKED_UP_BACKEND_STATUSES
    }

    // Packages currently on board the vehicle
    val filteredCurrent = state.driverCurrentPackages.filter {
        it.packageUuid !in incomingPackageUuids &&
        it.backendStatus !in NOT_PICKED_UP_BACKEND_STATUSES &&
        (searchQuery.isBlank() || it.id.contains(searchQuery, true) ||
        it.description.contains(searchQuery, true) || it.toAddress.contains(searchQuery, true) || it.fromAddress.contains(searchQuery, true))
    }.let { list ->
        val inProgress = list.filter { it.status != PackageStatus.DELIVERED && it.status != PackageStatus.CANCELLED && it.status != PackageStatus.PENDING_CONFIRMATION && !it.hasOpenTransfer }
        val pendingAction = list.filter { it.status == PackageStatus.PENDING_CONFIRMATION || it.hasOpenTransfer }
        val done = list.filter { it.status == PackageStatus.DELIVERED || it.status == PackageStatus.CANCELLED }
        inProgress.sortedByUnreadNotices(state.notices) + pendingAction + done
    }

    val driverUserId = state.authUser?.id
    val visibleOffers = state.driverAvailableOffers.filter { pkg ->
        val alreadyCustodian = driverUserId != null && pkg.custodians.any { it.userId == driverUserId }
        pkg.transferId != null && !alreadyCustodian && pkg.transferStatus != "REQUESTED"
    }

    val filteredTransfers = state.driverIncomingTransfers

    // ── Unified Incoming Items (Assigned + Transfers + Offers) ─────────────
    val incomingItems: List<IncomingPackageItem> = remember(
        assignedToDriverPackages,
        filteredTransfers,
        visibleOffers,
        incomingPackageUuids,
        searchQuery
    ) {
        val list = mutableListOf<IncomingPackageItem>()

        // 1. Direct Office Assignments (highest priority to pick up)
        assignedToDriverPackages.forEach { pkg ->
            list.add(IncomingPackageItem.Assigned(pkg))
        }

        // 2. Incoming Worker/Driver Transfers
        filteredTransfers.forEach { matched ->
            matched.packages.forEach { pkg ->
                list.add(IncomingPackageItem.Transfer(pkg, matched.transferId, matched.ruleType))
            }
        }

        // 3. Open Broadcast Offers
        visibleOffers.filter { it.packageUuid !in incomingPackageUuids }.forEach { pkg ->
            list.add(IncomingPackageItem.Offer(pkg, pkg.transferId ?: "", pkg.transferRuleType ?: "AUTO"))
        }

        if (searchQuery.isBlank()) {
            list
        } else {
            list.filter { item ->
                item.pkg.id.contains(searchQuery, true) ||
                item.pkg.description.contains(searchQuery, true) ||
                item.pkg.toAddress.contains(searchQuery, true) ||
                item.pkg.fromAddress.contains(searchQuery, true)
            }
        }
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isWide = screenWidthDp >= 600
    val gridCols = when {
        screenWidthDp >= 1000 -> GridCells.Adaptive(320.dp)
        screenWidthDp >= 600 -> GridCells.Adaptive(300.dp)
        else -> GridCells.Fixed(1)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )

    var isSearchExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val pagerState = rememberPagerState(pageCount = { 2 })

    // Sync pager → subTab
    LaunchedEffect(pagerState.currentPage) {
        if (subTab != pagerState.currentPage) {
            viewModel.setDriverPackageSubTab(pagerState.currentPage)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    // Sync subTab → pager
    LaunchedEffect(subTab) {
        if (pagerState.currentPage != subTab) {
            pagerState.animateScrollToPage(
                page = subTab,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
            )
        }
    }

    // Exit selection mode if switching away from On Board tab
    LaunchedEffect(subTab) {
        if (subTab != 0 && isSelection) viewModel.exitSelectionMode()
    }

    var isPullRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing && isPullRefreshing) {
            isPullRefreshing = false
        }
    }

    PullToRefreshBox(
        isRefreshing = isPullRefreshing && !isSelection,
        onRefresh = {
            isPullRefreshing = true
            viewModel.refreshDriverPackages()
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Selection mode compact bar ──────────────────────────────
                if (isSelection) {
                    SelectionChipRow(
                        selectedCount = selectedIds.size,
                        onCancel = { viewModel.exitSelectionMode() }
                    )
                }

                // ── Header Control: Search + 2-Segment Tab Bar + Notification Bell (Same line & height) ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
                        .animateContentSize(tween(220)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSearchExpanded) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateDriverPackageSearch(it) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            placeholder = { Text("Search by code, destination...", fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(Icons.Filled.Search, null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        viewModel.updateDriverPackageSearch("")
                                        isSearchExpanded = false
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Filled.Close, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                                }
                            },
                            shape = FluidShapes.button,
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = colors.divider,
                                focusedBorderColor = colors.purple,
                                cursorColor = colors.purple,
                                unfocusedLeadingIconColor = colors.textSecondary,
                                focusedLeadingIconColor = colors.purple
                            ),
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                        )
                        Spacer(Modifier.width(8.dp))
                        // Notification bell button (SAME HEIGHT 44.dp!)
                        FrostedFloatingIconButton(
                            onClick = onNoticesClick,
                            shape = FluidShapes.button,
                            modifier = Modifier.size(44.dp)
                        ) {
                            BadgedBox(badge = {
                                if (noticeCount > 0) {
                                    Badge(containerColor = colors.purple, contentColor = Color.White) {
                                        Text("$noticeCount", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }) {
                                Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                            }
                        }
                    } else {
                        // Search icon button
                        val searchInteractionSource = remember { MutableInteractionSource() }
                        AcrylicGlassSurface(
                            modifier = Modifier.size(44.dp),
                            shape = FluidShapes.button,
                            shadowElevation = 3.dp
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .fluidBounceClick(interactionSource = searchInteractionSource, onClick = { isSearchExpanded = true }),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Search, null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        // ── Modern 2-Segment Control (On Board vs Incoming) ─
                        AcrylicGlassSurface(
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = FluidShapes.button,
                            shadowElevation = 3.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val tabs = listOf(
                                    Triple(0, Icons.Filled.LocalShipping, filteredCurrent.size),
                                    Triple(1, Icons.Filled.MoveToInbox, incomingItems.size)
                                )

                                tabs.forEach { (index, icon, count) ->
                                    val isSelected = subTab == index
                                    val disabled = isSelection && index != 0

                                    val iconTint by animateColorAsState(
                                        targetValue = if (isSelected) colors.purple else colors.textSecondary,
                                        animationSpec = tween(250, easing = FastOutSlowInEasing),
                                        label = "topTabIcon$index"
                                    )
                                    val bgPill by animateColorAsState(
                                        targetValue = if (isSelected) colors.purple.copy(alpha = 0.12f) else Color.Transparent,
                                        animationSpec = tween(250, easing = FastOutSlowInEasing),
                                        label = "topTabBg$index"
                                    )
                                    val badgeBg by animateColorAsState(
                                        targetValue = if (isSelected) colors.purple else colors.divider,
                                        animationSpec = tween(250, easing = FastOutSlowInEasing),
                                        label = "topTabBadgeBg$index"
                                    )
                                    val badgeText by animateColorAsState(
                                        targetValue = if (isSelected) Color.White else colors.textSecondary,
                                        animationSpec = tween(250, easing = FastOutSlowInEasing),
                                        label = "topTabBadgeText$index"
                                    )

                                    val tabInteractionSource = remember { MutableInteractionSource() }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(FluidShapes.chip)
                                            .background(bgPill)
                                            .fluidBounceClick(
                                                scaleDown = 0.95f,
                                                interactionSource = tabInteractionSource
                                            ) {
                                                if (subTab != index && !disabled) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    viewModel.setDriverPackageSubTab(index)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = if (index == 0) "On Board" else "Incoming",
                                                tint = iconTint,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))

                                            // Count badge
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(badgeBg)
                                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "$count",
                                                    color = badgeText,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        // Notification bell button (SAME HEIGHT 44.dp!)
                        FrostedFloatingIconButton(
                            onClick = onNoticesClick,
                            shape = FluidShapes.button,
                            modifier = Modifier.size(44.dp)
                        ) {
                            BadgedBox(badge = {
                                if (noticeCount > 0) {
                                    Badge(containerColor = colors.purple, contentColor = Color.White) {
                                        Text("$noticeCount", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }) {
                                Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── Tab Content Pager (2 pages) ──────────────────────────────
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val listState = remember { LazyListState() }

                    when (page) {
                        0 -> {
                            // ── PAGE 0: ON BOARD (ACTIVE PACKAGES) ───────────
                            if (isDriverInitLoading && filteredCurrent.isEmpty() && !isSelection) {
                                ShimmerList(shimmerAlpha)
                            } else if (filteredCurrent.isEmpty()) {
                                EmptyPackagesState(
                                    title = "No packages on board",
                                    subtitle = "Packages you pick up or load will appear here"
                                )
                            } else if (isWide) {
                                LazyVerticalGrid(
                                    columns = gridCols,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = adaptiveHorizontalPadding(),
                                        end = adaptiveHorizontalPadding(),
                                        top = 4.dp,
                                        bottom = if (isSelection) 160.dp else 90.dp
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(filteredCurrent, key = { it.id }) { pkg ->
                                        if (isSelection) {
                                            val isReq = pkg.transferStatus == "REQUESTED"
                                            SelectableCurrentPackageCard(
                                                pkg = pkg,
                                                isSelected = pkg.id in selectedIds,
                                                canSelect = pkg.status != PackageStatus.DELIVERED && pkg.status != PackageStatus.CANCELLED && !isReq,
                                                onToggle = { viewModel.togglePackageSelection(pkg.id) },
                                                onDetail = { viewModel.openPackageDetail(pkg.id) }
                                            )
                                        } else {
                                            DriverCurrentPackageCard(
                                                pkg = pkg,
                                                onDeliver = { viewModel.openDeliverDialog(pkg.id) },
                                                onTransfer = { viewModel.openTransferDialog(pkg.id) },
                                                onCancelTransfer = { pkgId, tId -> viewModel.cancelTransferOffer(pkgId, tId) },
                                                isCancellingTransfer = state.isCancellingTransfer,
                                                onDetail = { viewModel.openPackageDetail(pkg.id) },
                                                onLongPress = {
                                                    if (pkg.status != PackageStatus.DELIVERED && pkg.status != PackageStatus.CANCELLED && !pkg.hasOpenTransfer) {
                                                        viewModel.startSelectionMode(pkg.id)
                                                    }
                                                },
                                                onPickup = { viewModel.pickupPackage(pkg.id) },
                                                isPickingUp = state.pickingUpPackageId == pkg.id,
                                                notices = state.notices
                                            )
                                        }
                                    }
                                    if (state.driverCurrentHasMore && !state.isLoadingMorePackages && !isRefreshing) {
                                        item { LaunchedEffect(Unit) { viewModel.loadMoreDriverCurrent() } }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 4.dp,
                                        bottom = if (isSelection) 160.dp else 90.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(filteredCurrent, key = { it.id }) { pkg ->
                                        if (isSelection) {
                                            val isReq = pkg.transferStatus == "REQUESTED"
                                            SelectableCurrentPackageCard(
                                                pkg = pkg,
                                                isSelected = pkg.id in selectedIds,
                                                canSelect = pkg.status != PackageStatus.DELIVERED && pkg.status != PackageStatus.CANCELLED && !isReq,
                                                onToggle = { viewModel.togglePackageSelection(pkg.id) },
                                                onDetail = { viewModel.openPackageDetail(pkg.id) }
                                            )
                                        } else {
                                            DriverCurrentPackageCard(
                                                pkg = pkg,
                                                onDeliver = { viewModel.openDeliverDialog(pkg.id) },
                                                onTransfer = { viewModel.openTransferDialog(pkg.id) },
                                                onCancelTransfer = { pkgId, tId -> viewModel.cancelTransferOffer(pkgId, tId) },
                                                isCancellingTransfer = state.isCancellingTransfer,
                                                onDetail = { viewModel.openPackageDetail(pkg.id) },
                                                onLongPress = {
                                                    if (pkg.status != PackageStatus.DELIVERED && pkg.status != PackageStatus.CANCELLED && !pkg.hasOpenTransfer) {
                                                        viewModel.startSelectionMode(pkg.id)
                                                    }
                                                },
                                                onPickup = { viewModel.pickupPackage(pkg.id) },
                                                isPickingUp = state.pickingUpPackageId == pkg.id,
                                                notices = state.notices
                                            )
                                        }
                                    }
                                    if (state.driverCurrentHasMore && !state.isLoadingMorePackages && !isRefreshing) {
                                        item { LaunchedEffect(Unit) { viewModel.loadMoreDriverCurrent() } }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // ── PAGE 1: INCOMING (OFFERS + TRANSFERS + ASSIGNED) ─
                            if (!state.driverHasVehicle) {
                                EmptyPackagesState(
                                    title = "Vehicle Required",
                                    subtitle = "You must have an assigned vehicle to access incoming packages"
                                )
                            } else if (isDriverInitLoading && incomingItems.isEmpty()) {
                                ShimmerList(shimmerAlpha)
                            } else if (incomingItems.isEmpty()) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    if (!state.hasActiveTrip) {
                                        NoActiveTripWarningBanner()
                                    }
                                    EmptyPackagesState(
                                        title = "No incoming packages",
                                        subtitle = "Offers, transfers, and assignments will appear here"
                                    )
                                }
                            } else if (isWide) {
                                LazyVerticalGrid(
                                    columns = gridCols,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = adaptiveHorizontalPadding(),
                                        end = adaptiveHorizontalPadding(),
                                        top = 4.dp,
                                        bottom = 90.dp
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (!state.hasActiveTrip) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            NoActiveTripWarningBanner()
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
                                            is IncomingPackageItem.Assigned -> {
                                                { viewModel.pickupPackage(pkg.id) }
                                            }
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

                                        val isThisAccepting = (state.isAcceptingTransfer && (state.acceptTransferPackageId == pkg.id || state.acceptTransferPackageId == null)) ||
                                                (state.pickingUpPackageId == pkg.id)

                                        DriverIncomingPackageCard(
                                            item = item,
                                            onAccept = acceptAction,
                                            onDetail = { viewModel.openPackageDetail(pkg.id) },
                                            isAccepting = isThisAccepting,
                                            hasActiveTrip = state.hasActiveTrip,
                                            isNew = pkg.id == state.newPackageFromSubscription?.id
                                        )
                                    }

                                    if (state.driverOffersHasMore && !state.isLoadingMorePackages && !isRefreshing) {
                                        item { LaunchedEffect(Unit) { viewModel.loadMoreDriverOffers() } }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 4.dp,
                                        bottom = 90.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (!state.hasActiveTrip) {
                                        item {
                                            NoActiveTripWarningBanner()
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
                                            is IncomingPackageItem.Assigned -> {
                                                { viewModel.pickupPackage(pkg.id) }
                                            }
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

                                        val isThisAccepting = (state.isAcceptingTransfer && (state.acceptTransferPackageId == pkg.id || state.acceptTransferPackageId == null)) ||
                                                (state.pickingUpPackageId == pkg.id)

                                        DriverIncomingPackageCard(
                                            item = item,
                                            onAccept = acceptAction,
                                            onDetail = { viewModel.openPackageDetail(pkg.id) },
                                            isAccepting = isThisAccepting,
                                            hasActiveTrip = state.hasActiveTrip,
                                            isNew = pkg.id == state.newPackageFromSubscription?.id
                                        )
                                    }

                                    if (state.driverOffersHasMore && !state.isLoadingMorePackages && !isRefreshing) {
                                        item { LaunchedEffect(Unit) { viewModel.loadMoreDriverOffers() } }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Floating Create Transfer button (selection mode) ─────────────
            val allDriverPackages = state.driverCurrentPackages + state.driverAvailableOffers
            val eligibleSelectedCount = selectedIds.count { id ->
                allDriverPackages.find { it.id == id }?.hasOpenTransfer != true
            }
            AnimatedVisibility(
                visible = isSelection && selectedIds.isNotEmpty(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 100.dp),
                enter = slideInVertically(tween(300)) { it / 2 } + scaleIn(tween(300)),
                exit = slideOutVertically(tween(200)) { it / 2 } + scaleOut(tween(200))
            ) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.openBatchTransferDialog() },
                    containerColor = colors.purple,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    icon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, null, modifier = Modifier.size(20.dp)) },
                    text = { Text("Create Transfer ($eligibleSelectedCount)", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                )
            }
        }
    }
}

// ── Selection Mode Compact Chip Row ──────────────────────────────────────────

@Composable
private fun SelectionChipRow(selectedCount: Int, onCancel: () -> Unit) {
    val colors = LocalDriversColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = colors.purple.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, colors.purple.copy(alpha = 0.3f))
        ) {
            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = colors.purple, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("$selectedCount selected", color = colors.purple, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onCancel,
            modifier = Modifier.heightIn(min = 32.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Cancel", color = colors.textSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
private fun NoActiveTripWarningBanner() {
    val colors = LocalDriversColors.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = colors.amber.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, colors.amber.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = colors.amber,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "No active trip — you cannot accept broadcast offers until a trip is scheduled for you",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun ShimmerList(alpha: Float) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) { items(5) { PackageSkeleton(alpha) } }
}

@Composable
private fun PackageSkeleton(alpha: Float) {
    val colors = LocalDriversColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface.copy(alpha = alpha),
        border = BorderStroke(1.dp, colors.divider.copy(alpha = alpha))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(80.dp, 12.dp).background(colors.textSecondary.copy(alpha = 0.2f), RoundedCornerShape(4.dp)))
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(20.dp).background(colors.textSecondary.copy(alpha = 0.2f), CircleShape))
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(48.dp).background(colors.textSecondary.copy(alpha = 0.1f), RoundedCornerShape(10.dp)))
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(14.dp).background(colors.textSecondary.copy(alpha = 0.15f), RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
fun EmptyPackagesState(title: String, subtitle: String) {
    val colors = LocalDriversColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().fillParentMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Inventory2, null, tint = colors.textSecondary, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(title, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(subtitle, color = colors.textSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}
