package com.gocavgo.twese.ui.client

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.gocavgo.twese.data.ClientPackage
import com.gocavgo.twese.data.ClientUser
import com.gocavgo.twese.data.Notice
import com.gocavgo.twese.data.PackageStatus
import com.gocavgo.twese.data.hasOpenTransfer
import com.gocavgo.twese.data.hasUnreadNotices
import com.gocavgo.twese.data.isActive
import com.gocavgo.twese.data.sortedByUnreadNotices
import com.gocavgo.twese.ui.common.AcrylicGlassSurface
import com.gocavgo.twese.ui.common.CachedAvatarImage
import com.gocavgo.twese.ui.common.FloatingHomeButton
import com.gocavgo.twese.ui.common.FluidShapes
import com.gocavgo.twese.ui.common.PackageStatusIconBadge
import com.gocavgo.twese.ui.common.contentMaxWidth
import com.gocavgo.twese.ui.common.fluidBounceClick
import com.gocavgo.twese.ui.common.formatTime
import com.gocavgo.twese.ui.common.isWideScreen
import com.gocavgo.twese.ui.theme.CavgoGreen
import com.gocavgo.twese.ui.theme.LocalDriversColors
import com.gocavgo.twese.util.PhoneValidation
import com.gocavgo.twese.viewmodel.AppThemeMode
import com.gocavgo.twese.viewmodel.DataState
import com.gocavgo.twese.viewmodel.DriverProfile

// ── Dock Window Tab Enum ──────────────────────────────────────────────────────

enum class ClientDockTab {
    PROFILE,
    SEARCH,
    NOTIFICATIONS
}

// ── Passenger / Client Home Screen ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientHomeScreen(
    client: ClientUser,
    packages: List<ClientPackage>,
    themeMode: AppThemeMode,
    isClientProfileMenuOpen: Boolean,
    isClientSettingsOpen: Boolean,
    onCreatePackage: () -> Unit,
    onTrackPackage: (String) -> Unit,
    onLogout: () -> Unit,
    onProfileMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    isRefreshing: Boolean = false,
    isInitialLoading: Boolean = false,
    onRefresh: () -> Unit = {},
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onCloseSettings: () -> Unit,
    onDismissMenus: () -> Unit,
    onCreateTransfer: (String) -> Unit = {},
    onConfirmTransfer: (String, String) -> Unit = { _, _ -> },
    onRejectTransfer: (String, String) -> Unit = { _, _ -> },
    onGeneratePickupCode: (String) -> Unit = {},
    hasUnsavedDraft: Boolean = false,
    packagesFetchedOnce: Boolean = false,
    clientDataState: DataState = DataState.UNKNOWN,
    onConfirmDeliveryDirect: (ClientPackage) -> Unit = {},
    onNoticesClick: () -> Unit = {},
    noticeCount: Int = 0,
    notices: List<Notice> = emptyList(),
    onRequestDriver: (() -> Unit)? = null,
    driverRequestStatus: String? = null,
    driverRequestCompanyCode: String? = null,
    driverRequestCompanyName: String? = null,
    driverRequestRejectionReason: String? = null
) {
    val colors = LocalDriversColors.current
    val wide = isWideScreen()
    val maxW = contentMaxWidth()

    var activeDockTab by remember { mutableStateOf<ClientDockTab?>(null) }
    var isCompletedPackagesPageOpen by rememberSaveable { mutableStateOf(false) }

    // Sync external menu triggers from MainActivity / ViewModel
    LaunchedEffect(isClientProfileMenuOpen, isClientSettingsOpen) {
        if (isClientProfileMenuOpen || isClientSettingsOpen) {
            activeDockTab = ClientDockTab.PROFILE
        }
    }

    // Single source of active vs completed packages
    val activePackages = remember(packages, notices) {
        packages.filter { it.isActive() }.sortedByUnreadNotices(notices)
    }
    val completedPackages = remember(packages) {
        packages.filter { !it.isActive() }
    }

    // Auto-open create package panel ONLY when backend confirms zero active packages
    var preventedAutoOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(clientDataState) {
        if (clientDataState == DataState.NO_DATA && !preventedAutoOpen) {
            preventedAutoOpen = true
            onCreatePackage()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )

    val density = LocalDensity.current
    val statusBarHeightDp = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val gradientHeightDp = (statusBarHeightDp + 24.dp).coerceAtLeast(1.dp)
    val listTopPadding = gradientHeightDp + 8.dp
    val solidStopFraction = (statusBarHeightDp / gradientHeightDp).coerceIn(0f, 1f)

    var isPullRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing && isPullRefreshing) {
            isPullRefreshing = false
        }
    }

    val profile = remember(client) {
        DriverProfile(
            name = client.name,
            email = client.email,
            phone = client.phone,
            username = client.username,
            avatarUrl = client.avatarUrl
        )
    }

    if (isCompletedPackagesPageOpen) {
        PassengerCompletedPackagesScreen(
            completedPackages = completedPackages,
            client = client,
            notices = notices,
            onHomeClick = { isCompletedPackagesPageOpen = false },
            onTrackPackage = onTrackPackage,
            onCreateTransfer = onCreateTransfer,
            onConfirmTransfer = onConfirmTransfer,
            onRejectTransfer = onRejectTransfer,
            onGeneratePickupCode = onGeneratePickupCode,
            onConfirmDeliveryDirect = onConfirmDeliveryDirect
        )
    } else {
        Box(modifier = Modifier.fillMaxSize().background(colors.background)) {

        // ── 1. Single Page for Active Packages (Primary Screen Feed) ─────────
        PullToRefreshBox(
            isRefreshing = isPullRefreshing,
            onRefresh = {
                isPullRefreshing = true
                onRefresh()
            },
            modifier = Modifier.fillMaxSize()
        ) {
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
                    val activeListState = remember { LazyListState() }
                    val isStillLoading = (isInitialLoading || !packagesFetchedOnce || clientDataState == DataState.LOADING) && activePackages.isEmpty()

                    if (isStillLoading) {
                        LazyColumn(
                            state = activeListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = listTopPadding, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(5) { PackageSkeleton(shimmerAlpha) }
                        }
                    } else if (activePackages.isEmpty()) {
                        LazyColumn(
                            state = activeListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = listTopPadding, bottom = 120.dp)
                        ) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 100.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Surface(
                                            modifier = Modifier.size(72.dp),
                                            shape = CircleShape,
                                            color = colors.purple.copy(alpha = 0.10f),
                                            border = BorderStroke(1.dp, colors.purple.copy(alpha = 0.25f))
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Filled.LocalShipping,
                                                    null,
                                                    tint = colors.purple,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(14.dp))
                                        Text(
                                            "No active packages",
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Packages currently on the move will appear here.",
                                            color = colors.textSecondary,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = activeListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = listTopPadding, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(items = activePackages, key = { it.id }) { pkg ->
                                PackageCard(
                                    pkg = pkg,
                                    onClick = { onTrackPackage(pkg.id) },
                                    clientId = client.id,
                                    clientPhone = client.phone,
                                    onCreateTransfer = { onCreateTransfer(pkg.id) },
                                    onConfirmTransfer = { transferId -> onConfirmTransfer(pkg.id, transferId) },
                                    onRejectTransfer = { transferId -> onRejectTransfer(pkg.id, transferId) },
                                    onGeneratePickupCode = onGeneratePickupCode,
                                    onConfirmDeliveryDirect = onConfirmDeliveryDirect,
                                    notices = notices,
                                    modifier = Modifier.animateItem(tween(300))
                                )
                            }
                            item {
                                if (!isRefreshing && !isLoadingMore && activePackages.isNotEmpty()) {
                                    LaunchedEffect(Unit) { onLoadMore() }
                                }
                            }
                        }
                    }

                    // Status bar overlay: solid behind status bar, smooth gradient below
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(gradientHeightDp)
                            .background(
                                Brush.verticalGradient(
                                    0.0f to colors.background,
                                    solidStopFraction to colors.background,
                                    1.0f to colors.background.copy(alpha = 0f)
                                )
                            )
                    )
                }
            }
        }

        // ── 2. Modal Scrim Backdrop for Popup Window ─────────────────────────
        AnimatedVisibility(
            visible = activeDockTab != null,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.40f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        activeDockTab = null
                        onDismissMenus()
                    }
            )
        }

        // ── 3. Reused Floating Dock Window (Above the Dock) ──────────────────
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
            val windowMod = if (wide && maxW != Dp.Unspecified) {
                Modifier.widthIn(max = 500.dp).heightIn(max = 540.dp).fillMaxHeight(0.74f)
            } else {
                Modifier.fillMaxWidth().heightIn(max = 540.dp).fillMaxHeight(0.74f)
            }

            AcrylicGlassSurface(
                modifier = windowMod,
                shape = FluidShapes.dock,
                backgroundColor = colors.surface.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, colors.divider.copy(alpha = 0.8f)),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Top drag/pill handle
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 10.dp)
                            .size(width = 38.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(colors.divider)
                    )

                    // Window Dynamic Content
                    Box(modifier = Modifier.weight(1f)) {
                        when (activeDockTab) {
                            ClientDockTab.PROFILE -> {
                                ClientProfileSheetContent(
                                    client = client,
                                    profile = profile,
                                    themeMode = themeMode,
                                    completedPackagesCount = completedPackages.size,
                                    onOpenCompletedPackages = {
                                        activeDockTab = null
                                        isCompletedPackagesPageOpen = true
                                    },
                                    onThemeModeChange = onThemeModeChange,
                                    onProfileClick = onProfileClick,
                                    onRequestDriver = onRequestDriver,
                                    driverRequestStatus = driverRequestStatus,
                                    driverRequestCompanyCode = driverRequestCompanyCode,
                                    driverRequestCompanyName = driverRequestCompanyName,
                                    driverRequestRejectionReason = driverRequestRejectionReason,
                                    onLogout = onLogout
                                )
                            }
                            ClientDockTab.SEARCH -> {
                                ClientSearchSheetContent(
                                    allPackages = packages,
                                    onTrackPackage = { pkgId ->
                                        activeDockTab = null
                                        onTrackPackage(pkgId)
                                    }
                                )
                            }
                            ClientDockTab.NOTIFICATIONS -> {
                                ClientNotificationsSheetContent(
                                    notices = notices,
                                    onNoticeClick = { notice ->
                                        if (notice.resourceId.isNotBlank()) {
                                            activeDockTab = null
                                            onTrackPackage(notice.resourceId)
                                        }
                                    }
                                )
                            }
                            null -> {}
                        }
                    }
                }
            }
        }

        // ── 4. Floating Action (+ Add Package) and Bottom Passenger Dock ───────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            // Floating Create Package FAB (above dock, right side)
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
                        .fluidBounceClick(scaleDown = 0.90f) { onCreatePackage() },
                    shape = CircleShape,
                    color = colors.purple,
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (hasUnsavedDraft) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = colors.red,
                                        contentColor = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = "New Package",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        } else {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "New Package",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }

            // Floating Passenger Dock Bar
            val dockMod = if (wide && maxW != Dp.Unspecified) {
                Modifier.widthIn(max = 480.dp).padding(horizontal = 16.dp).align(Alignment.CenterHorizontally)
            } else {
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            }

            PassengerDock(
                activeTab = activeDockTab,
                onTabClick = { tab ->
                    if (activeDockTab == tab) {
                        activeDockTab = null
                        onDismissMenus()
                    } else {
                        activeDockTab = tab
                    }
                },
                profile = profile,
                unreadNoticesCount = noticeCount,
                modifier = dockMod
            )
        }
    }
}
}

// ── Passenger Floating Dock Component ─────────────────────────────────────────

@Composable
fun PassengerDock(
    activeTab: ClientDockTab?,
    onTabClick: (ClientDockTab) -> Unit,
    profile: DriverProfile,
    unreadNoticesCount: Int,
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
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Profile Avatar Button
            val isProfileActive = activeTab == ClientDockTab.PROFILE
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
                    .fluidBounceClick(scaleDown = 0.90f) { onTabClick(ClientDockTab.PROFILE) },
                contentAlignment = Alignment.Center
            ) {
                val avatarUrl = profile.avatarUrl
                if (!avatarUrl.isNullOrBlank()) {
                    CachedAvatarImage(remoteUrl = avatarUrl, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text(
                        profile.name.firstOrNull()?.uppercase() ?: "P",
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
                isActive = activeTab == ClientDockTab.SEARCH,
                onClick = { onTabClick(ClientDockTab.SEARCH) }
            )

            // 3. Notifications Icon Button
            DockIconButton(
                icon = Icons.Filled.Notifications,
                contentDescription = "Notifications",
                isActive = activeTab == ClientDockTab.NOTIFICATIONS,
                badgeCount = unreadNoticesCount,
                onClick = { onTabClick(ClientDockTab.NOTIFICATIONS) }
            )
        }
    }
}

// ── Dock Icon Button Helper ───────────────────────────────────────────────────

@Composable
private fun DockIconButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    badgeCount: Int = 0,
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
            .size(42.dp)
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
                }
            }
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ── Client Profile Sheet Content (Main View) ──────────────────────────────────

@Composable
private fun ClientProfileSheetContent(
    client: ClientUser,
    profile: DriverProfile,
    themeMode: AppThemeMode,
    completedPackagesCount: Int,
    onOpenCompletedPackages: () -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onProfileClick: () -> Unit,
    onRequestDriver: (() -> Unit)?,
    driverRequestStatus: String?,
    driverRequestCompanyCode: String?,
    driverRequestCompanyName: String?,
    driverRequestRejectionReason: String?,
    onLogout: () -> Unit
) {
    val colors = LocalDriversColors.current
    var isThemeDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Row 1: Completed Packages Tile (Primary User Feature) + Theme Tile ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Completed Packages Tile
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp)
                    .fluidBounceClick(scaleDown = 0.93f) { onOpenCompletedPackages() },
                shape = FluidShapes.card,
                color = colors.purple.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, colors.purple.copy(alpha = 0.35f))
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
                            .background(colors.purple.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Completed Packages",
                            tint = colors.purple,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Completed",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1
                        )
                        Text(
                            "$completedPackagesCount packages",
                            fontSize = 11.sp,
                            color = colors.purple,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = colors.purple,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Theme Switcher Tile (with Dropdown)
            Box(modifier = Modifier.weight(1f)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
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
        }

        // ── Row 2: Driver Request Button (if available) ───────────────────────
        if (onRequestDriver != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .fluidBounceClick(scaleDown = 0.94f) {
                        if (driverRequestStatus == null) onRequestDriver()
                    },
                shape = FluidShapes.card,
                color = colors.surfaceAlt,
                border = BorderStroke(1.dp, colors.divider.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (driverRequestStatus == "PENDING") colors.amber.copy(alpha = 0.16f)
                                else colors.purple.copy(alpha = 0.14f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.DirectionsCar,
                            contentDescription = "Driver Request",
                            tint = if (driverRequestStatus == "PENDING") colors.amber else colors.purple,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (driverRequestStatus) {
                                "PENDING" -> "Driver Request Pending"
                                "APPROVED" -> "Driver Approved"
                                "REJECTED" -> "Driver Request Rejected"
                                else -> "Become a Driver"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (driverRequestStatus) {
                                "PENDING" -> colors.amber
                                "APPROVED" -> CavgoGreen
                                "REJECTED" -> colors.red
                                else -> colors.textPrimary
                            }
                        )
                        Text(
                            when (driverRequestStatus) {
                                "PENDING" -> "Awaiting fleet manager approval"
                                "APPROVED" -> "Approved by company"
                                "REJECTED" -> driverRequestRejectionReason?.ifBlank { "Tap to try again" } ?: "Tap to try again"
                                else -> "Apply to drive for a transport company"
                            },
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f, fill = false))

        // ── Row 3: Symmetric Profile and Logout Buttons ───────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // User Profile (Avatar + Name only, equal width)
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
                                profile.name.firstOrNull()?.uppercase() ?: "P",
                                color = colors.purple,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            profile.name.ifBlank { "User Profile" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            PhoneValidation.toDisplayFormat(profile.phone).ifBlank { profile.email.ifBlank { "Passenger" } },
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onProfileClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = "Edit Profile",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Logout Button (Equal width)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .fluidBounceClick(scaleDown = 0.93f) { onLogout() },
                shape = FluidShapes.card,
                color = colors.red.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, colors.red.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
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
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.red
                    )
                }
            }
        }
    }
}

// ── Passenger Completed Packages Screen (Full Page with Floating Home Button) ──

@Composable
fun PassengerCompletedPackagesScreen(
    completedPackages: List<ClientPackage>,
    client: ClientUser,
    notices: List<Notice>,
    onHomeClick: () -> Unit,
    onTrackPackage: (String) -> Unit,
    onCreateTransfer: (String) -> Unit,
    onConfirmTransfer: (String, String) -> Unit,
    onRejectTransfer: (String, String) -> Unit,
    onGeneratePickupCode: (String) -> Unit,
    onConfirmDeliveryDirect: (ClientPackage) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDriversColors.current
    val wide = isWideScreen()
    val maxW = contentMaxWidth()
    var searchQuery by remember { mutableStateOf("") }

    BackHandler { onHomeClick() }

    val filteredList = remember(completedPackages, searchQuery) {
        if (searchQuery.isBlank()) completedPackages
        else completedPackages.filter {
            it.id.contains(searchQuery, ignoreCase = true) ||
                    it.description.contains(searchQuery, ignoreCase = true) ||
                    it.recipientName.contains(searchQuery, ignoreCase = true) ||
                    it.fromAddress.contains(searchQuery, ignoreCase = true) ||
                    it.toAddress.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        val contentMod = if (wide && maxW != Dp.Unspecified) {
            Modifier.widthIn(max = maxW).fillMaxHeight().align(Alignment.TopCenter)
        } else {
            Modifier.fillMaxSize()
        }

        Column(
            modifier = contentMod
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onHomeClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Completed Packages",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = colors.purple.copy(alpha = 0.12f)
                ) {
                    Text(
                        "${completedPackages.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.purple,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            // Search bar if there are multiple completed packages
            if (completedPackages.size > 2) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    placeholder = { Text("Filter past packages...", color = colors.textSecondary, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = colors.purple, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Filled.Close, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = FluidShapes.button,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = colors.divider,
                        focusedBorderColor = colors.purple,
                        cursorColor = colors.purple
                    )
                )
            }

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 60.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            color = colors.purple.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, colors.purple.copy(alpha = 0.25f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (searchQuery.isNotBlank()) Icons.Filled.SearchOff else Icons.Filled.CheckCircle,
                                    null,
                                    tint = colors.purple,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            if (searchQuery.isNotBlank()) "No packages matching \"$searchQuery\""
                            else "No completed packages yet",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (searchQuery.isNotBlank()) "Try searching with a different term."
                            else "Delivered packages will appear here.",
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp)
                ) {
                    items(items = filteredList, key = { it.id }) { pkg ->
                        PackageCard(
                            pkg = pkg,
                            onClick = { onTrackPackage(pkg.id) },
                            clientId = client.id,
                            clientPhone = client.phone,
                            onCreateTransfer = { onCreateTransfer(pkg.id) },
                            onConfirmTransfer = { transferId -> onConfirmTransfer(pkg.id, transferId) },
                            onRejectTransfer = { transferId -> onRejectTransfer(pkg.id, transferId) },
                            onGeneratePickupCode = onGeneratePickupCode,
                            onConfirmDeliveryDirect = onConfirmDeliveryDirect,
                            notices = notices,
                            modifier = Modifier.animateItem(tween(250))
                        )
                    }
                }
            }
        }

        // Floating Home Button (Floating at bottom center)
        FloatingHomeButton(
            onClick = onHomeClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        )
    }
}

// ── Client Search Sheet Content ───────────────────────────────────────────────

@Composable
private fun ClientSearchSheetContent(
    allPackages: List<ClientPackage>,
    onTrackPackage: (String) -> Unit
) {
    val colors = LocalDriversColors.current
    var query by remember { mutableStateOf("") }

    val results = remember(allPackages, query) {
        if (query.isBlank()) emptyList()
        else allPackages.filter {
            it.id.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true) ||
                    it.senderName.contains(query, ignoreCase = true) ||
                    it.recipientName.contains(query, ignoreCase = true) ||
                    it.fromAddress.contains(query, ignoreCase = true) ||
                    it.toAddress.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by tracking code, name or stop...", color = colors.textSecondary, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = colors.purple, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Filled.Close, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = FluidShapes.button,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = colors.divider,
                focusedBorderColor = colors.purple,
                cursorColor = colors.purple
            )
        )

        Spacer(Modifier.height(10.dp))

        if (query.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Search, null, tint = colors.textSecondary.copy(alpha = 0.4f), modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Type a tracking number or destination", color = colors.textSecondary, fontSize = 13.sp)
                }
            }
        } else if (results.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.SearchOff, null, tint = colors.textSecondary.copy(alpha = 0.5f), modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No packages found for \"$query\"", color = colors.textSecondary, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(results, key = { it.id }) { pkg ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fluidBounceClick(scaleDown = 0.96f) { onTrackPackage(pkg.id) },
                        shape = FluidShapes.card,
                        color = colors.surfaceAlt,
                        border = BorderStroke(1.dp, colors.divider)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PackageStatusIconBadge(status = pkg.status, size = 26.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pkg.id, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${pkg.fromAddress} → ${pkg.toAddress}",
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

// ── Client Notifications Sheet Content ────────────────────────────────────────

@Composable
private fun ClientNotificationsSheetContent(
    notices: List<Notice>,
    onNoticeClick: (Notice) -> Unit
) {
    val colors = LocalDriversColors.current

    if (notices.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.NotificationsNone,
                    contentDescription = null,
                    tint = colors.textSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text("No notifications yet", color = colors.textSecondary, fontSize = 13.sp)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(notices, key = { it.id }) { notice ->
                val isUnread = notice.viewerReadAt == null
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fluidBounceClick(scaleDown = 0.97f) { onNoticeClick(notice) },
                    shape = FluidShapes.card,
                    color = if (isUnread) colors.purple.copy(alpha = 0.08f) else colors.surfaceAlt,
                    border = BorderStroke(
                        1.dp,
                        if (isUnread) colors.purple.copy(alpha = 0.35f) else colors.divider
                    )
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
                            Text(notice.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            Spacer(Modifier.height(2.dp))
                            Text(notice.message, fontSize = 12.sp, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// ── Skeleton Loader ───────────────────────────────────────────────────────────

@Composable
private fun PackageSkeleton(alpha: Float) {
    val colors = LocalDriversColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.surface.copy(alpha = alpha),
        border = BorderStroke(1.dp, colors.divider.copy(alpha = alpha))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(80.dp, 10.dp).background(colors.textSecondary.copy(alpha = 0.2f), RoundedCornerShape(4.dp)))
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(16.dp).background(colors.textSecondary.copy(alpha = 0.2f), CircleShape))
            }
            Spacer(Modifier.height(12.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Box(Modifier.size(40.dp, 8.dp).background(colors.textSecondary.copy(alpha = 0.1f), RoundedCornerShape(4.dp)))
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.size(70.dp, 12.dp).background(colors.textSecondary.copy(alpha = 0.2f), RoundedCornerShape(4.dp)))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Box(Modifier.size(40.dp, 8.dp).background(colors.textSecondary.copy(alpha = 0.1f), RoundedCornerShape(4.dp)))
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.size(70.dp, 12.dp).background(colors.textSecondary.copy(alpha = 0.2f), RoundedCornerShape(4.dp)))
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(10.dp).background(colors.textSecondary.copy(alpha = 0.1f), RoundedCornerShape(4.dp)))
        }
    }
}

// ── Package Card ──────────────────────────────────────────────────────────────

@Composable
private fun PackageCard(
    pkg: ClientPackage,
    onClick: () -> Unit,
    clientId: String = "",
    clientPhone: String = "",
    onCreateTransfer: (String) -> Unit = {},
    onConfirmTransfer: (String) -> Unit = {},
    onRejectTransfer: (String) -> Unit = {},
    onGeneratePickupCode: (String) -> Unit = {},
    onConfirmDeliveryDirect: (ClientPackage) -> Unit = {},
    notices: List<Notice> = emptyList(),
    modifier: Modifier = Modifier
) {
    val colors = LocalDriversColors.current
    val isUnread = pkg.hasUnreadNotices(notices)
    val isDelivered = pkg.status == PackageStatus.DELIVERED
    val isCancelled = pkg.status == PackageStatus.CANCELLED
    val isMyPackage = pkg.senderId == clientId
    val isForMe = pkg.recipientId == clientId
    val senderDisplayPhone = PhoneValidation.toDisplayFormat(pkg.senderPhone.ifBlank { if (isMyPackage) clientPhone else "" })
    val recipientDisplayPhone = PhoneValidation.toDisplayFormat(pkg.recipientPhone.ifBlank { if (isForMe) clientPhone else "" })

    // Check server transfers and custodians for button visibility
    val hasActiveTransfer = pkg.hasOpenTransfer
    val hasNoCustodians = pkg.custodians.isEmpty()
    val isTransferRequested = pkg.transfers.any { it.status == "REQUESTED" }
    val canCreateTransfer = !isDelivered && !isCancelled && !hasActiveTransfer && hasNoCustodians
    val canConfirmTransfer = !isDelivered && !isCancelled && isTransferRequested
    val canGeneratePickupCode = pkg.custodians.isNotEmpty() && pkg.packageUuid.isNotBlank()
            && !isDelivered && !isCancelled && pkg.status != PackageStatus.PENDING
    val isStale = !isDelivered && !isCancelled && !hasActiveTransfer && hasNoCustodians && pkg.status == PackageStatus.PENDING

    Surface(
        modifier = modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, if (pkg.status == PackageStatus.PENDING_CONFIRMATION) colors.purple.copy(alpha = 0.5f) else colors.divider)
    ) {
        Column(Modifier.padding(12.dp)) {
            // High-visibility pending confirmation banner
            if (pkg.status == PackageStatus.PENDING_CONFIRMATION) {
                val formattedCode = if (pkg.deliveryCode.length == 6) {
                    "${pkg.deliveryCode.take(3)} ${pkg.deliveryCode.takeLast(3)}"
                } else if (pkg.deliveryCode.isNotBlank()) {
                    pkg.deliveryCode
                } else {
                    ""
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clickable { onConfirmDeliveryDirect(pkg) },
                    shape = RoundedCornerShape(10.dp),
                    color = colors.purpleLight,
                    border = BorderStroke(1.2.dp, colors.purple.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.purple)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Driver has arrived!",
                            color = colors.purple,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (formattedCode.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Code: ",
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = formattedCode,
                                color = colors.purple,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.purple,
                            modifier = Modifier.clickable { onConfirmDeliveryDirect(pkg) }
                        ) {
                            Text(
                                text = "Tap to Confirm",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Unread notice dot
                if (isUnread) {
                    val dotTransition = rememberInfiniteTransition(label = "unread")
                    val dotAlpha by dotTransition.animateFloat(
                        initialValue = 0.35f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                        label = "dotAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(colors.red.copy(alpha = dotAlpha))
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(pkg.id, color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                if (canGeneratePickupCode) {
                    IconButton(
                        onClick = { onGeneratePickupCode(pkg.packageUuid) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "Generate pickup code",
                            tint = colors.purple,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                PackageStatusIconBadge(
                    status = pkg.status,
                    isStale = isStale,
                    size = 26.dp
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sender", color = colors.textSecondary, fontSize = 12.sp)
                    Text(if (pkg.senderId == clientId) "You" else pkg.senderName, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (senderDisplayPhone.isNotBlank()) Text(senderDisplayPhone, color = colors.textSecondary, fontSize = 12.sp)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Recipient", color = colors.textSecondary, fontSize = 12.sp)
                    Text(if (pkg.recipientId == clientId) "You" else pkg.recipientName, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (recipientDisplayPhone.isNotBlank()) Text(recipientDisplayPhone, color = colors.textSecondary, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pkg.fromAddress, color = colors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = colors.textSecondary, modifier = Modifier.size(12.dp))
                Text(pkg.toAddress, color = colors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }

            // Transfer action buttons
            if (canCreateTransfer || canConfirmTransfer) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canCreateTransfer) {
                        OutlinedButton(
                            onClick = { onCreateTransfer(pkg.id) },
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, colors.purple.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.CompareArrows, null, modifier = Modifier.size(14.dp), tint = colors.purple)
                            Spacer(Modifier.width(5.dp))
                            Text("Create Transfer", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.purple)
                        }
                    }
                    if (canConfirmTransfer) {
                        Button(
                            onClick = { pkg.transferId?.let { onConfirmTransfer(it) } },
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.purple)
                        ) {
                            Icon(Icons.Filled.Verified, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Confirm", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { pkg.transferId?.let { onRejectTransfer(it) } },
                            modifier = Modifier.height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Filled.Close, null, modifier = Modifier.size(14.dp), tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}
