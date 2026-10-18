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
import androidx.compose.foundation.border
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
    viewModel: TripViewModel
) {
    val state by viewModel.state.collectAsState()
    val colors = LocalDriversColors.current
    LaunchedEffect(Unit) { viewModel.loadDriverPackages() }

    val searchQuery = state.driverPackageSearchQuery
    val isRefreshing = state.isRefreshingPackages
    val isDriverInitLoading = state.isDriverInitialLoading
    val isSelection = state.isSelectionMode
    val selectedIds = state.selectedPackageIds

    val incomingPackageUuids = remember(state.driverIncomingTransfers) {
        state.driverIncomingTransfers.flatMap { it.packages.map { pkg -> pkg.packageUuid } }.toSet()
    }
    val NOT_PICKED_UP_BACKEND_STATUSES = remember { setOf("ORIGIN_OFFICE", "ASSIGNED_DRIVER") }

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

    var isPullRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing && isPullRefreshing) {
            isPullRefreshing = false
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    PullToRefreshBox(
        isRefreshing = isPullRefreshing && !isSelection,
        onRefresh = {
            isPullRefreshing = true
            viewModel.refreshDriverPackages()
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ── The Scrollable Package List (Infinite scroll edge-to-edge) ──
            if (isDriverInitLoading && filteredCurrent.isEmpty() && !isSelection) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = statusBarTop + 16.dp, bottom = navBarBottom + 88.dp)
                ) {
                    ShimmerList(shimmerAlpha)
                }
            } else if (filteredCurrent.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = statusBarTop + 16.dp, bottom = navBarBottom + 88.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyPackagesState(
                        title = "No packages on board",
                        subtitle = "Packages you pick up or load will appear here"
                    )
                }
            } else if (isWide) {
                LazyVerticalGrid(
                    columns = gridCols,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = adaptiveHorizontalPadding(),
                        end = adaptiveHorizontalPadding(),
                        top = statusBarTop + 16.dp,
                        bottom = if (isSelection) navBarBottom + 160.dp else navBarBottom + 88.dp
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
                val listState = remember { LazyListState() }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = statusBarTop + 16.dp,
                        bottom = if (isSelection) navBarBottom + 160.dp else navBarBottom + 88.dp
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

            // ── Selection Mode Compact Chip Bar (Floating at top) ───────────
            if (isSelection) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 10.dp)
                ) {
                    SelectionChipRow(
                        selectedCount = selectedIds.size,
                        onCancel = { viewModel.exitSelectionMode() }
                    )
                }
            }

            // ── Floating Create Transfer Button (Selection Mode) ────────────
            val allDriverPackages = state.driverCurrentPackages + state.driverAvailableOffers
            val eligibleSelectedCount = selectedIds.count { id ->
                allDriverPackages.find { it.id == id }?.hasOpenTransfer != true
            }
            AnimatedVisibility(
                visible = isSelection && selectedIds.isNotEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 90.dp),
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
