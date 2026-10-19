package com.gocavgo.twese.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gocavgo.twese.ui.theme.LocalDriversColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ── Continuous Spring Physics (OxygenOS / ColorOS inspired) ──────────────────

/**
 * Applies continuous spring scale animation on press (stiffness = Medium, dampingRatio = 0.75f).
 * Makes interactive surfaces feel physical, tactile, and alive.
 */
fun Modifier.fluidBounceClick(
    scaleDown: Float = 0.96f,
    interactionSource: MutableInteractionSource? = null,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val effectiveSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by effectiveSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "fluidBounceScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = effectiveSource,
                    indication = null,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )
}

// ── Standard Continuous Squircle & Pill Design Tokens ───────────────────────

/**
 * Standard continuous squircle & pill shape scale for consistent modern roundness.
 * Proportional scaling across component sizes (OxygenOS Aquamorphic / One UI inspired).
 */
object FluidShapes {
    /** Floating docks, bottom navigation bars, full-width dynamic activity capsules */
    val dock = RoundedCornerShape(26.dp)

    /** Dynamic top capsules, floating car plate pills, floating headers */
    val capsule = RoundedCornerShape(22.dp)

    /** Standard content cards, modals, hero containers, vehicle info */
    val card = RoundedCornerShape(20.dp)

    /** Interactive buttons, text fields, action sliders, floating square buttons */
    val button = RoundedCornerShape(16.dp)

    /** Compact chips, tags, status tooltips, sub-segment pills */
    val chip = RoundedCornerShape(12.dp)

    /** Full round pills, floating circular action buttons, slider thumbs, avatar frames */
    val pill = CircleShape
}

// ── Frosted Acrylic Glass Container ──────────────────────────────────────────

/**
 * Modern frosted acrylic glass container inspired by OxygenOS Aquamorphic / One UI.
 * Combines translucent surface with subtle divider border and smooth squircle corners.
 */
@Composable
fun AcrylicGlassSurface(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = FluidShapes.dock,
    backgroundColor: Color? = null,
    border: BorderStroke? = null,
    shadowElevation: androidx.compose.ui.unit.Dp = 4.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LocalDriversColors.current
    val effectiveBg = backgroundColor ?: colors.surface.copy(alpha = 0.88f)
    val effectiveBorder = border ?: BorderStroke(1.dp, colors.divider.copy(alpha = 0.7f))

    Surface(
        modifier = modifier,
        shape = shape,
        color = effectiveBg,
        border = effectiveBorder,
        shadowElevation = shadowElevation
    ) {
        Box(content = content)
    }
}

/**
 * Frosted acrylic glass floating button for secondary floating actions
 * (e.g. notifications bell, quick search, floating tool heads).
 */
@Composable
fun FrostedFloatingIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = FluidShapes.pill,
    icon: @Composable () -> Unit
) {
    val colors = LocalDriversColors.current
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        onClick = onClick,
        shape = shape,
        color = colors.surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, colors.divider.copy(alpha = 0.7f)),
        shadowElevation = 4.dp,
        interactionSource = interactionSource,
        modifier = modifier.fluidBounceClick(scaleDown = 0.90f, interactionSource = interactionSource)
    ) {
        Box(
            modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
    }
}

// ── Live Fluid Cloud Capsule (Dynamic Activity Pill) ─────────────────────────

/**
 * OxygenOS Fluid Cloud / Capsule Pill:
 * Displays dynamic live status at the top of the screen (e.g. active trip, next stop, live packages).
 * Single tap expands into active trip or detail view.
 */
@Composable
fun FluidCloudCapsule(
    visible: Boolean,
    title: String,
    subtitle: String,
    icon: ImageVector = Icons.Filled.DirectionsCar,
    accentColor: Color? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDriversColors.current
    val accent = accentColor ?: colors.purple

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.75f)) +
                slideInVertically(spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.75f)) { -it },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it },
        modifier = modifier
    ) {
        AcrylicGlassSurface(
            shape = FluidShapes.dock,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fluidBounceClick(scaleDown = 0.97f, onClick = onClick)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pulsing live indicator
                val pulseAnim = rememberInfiniteTransition(label = "capsulePulse")
                val pulseScale by pulseAnim.animateFloat(
                    initialValue = 0.88f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        tween(900, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = title,
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (subtitle.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Trailing arrow pill
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceAlt),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// ── Swipe-to-Complete Action Slider (Tier-1 Logistics / Driver Ergonomics) ───

/**
 * High-contrast swipe-to-complete slider for critical driver operations (e.g. Pick Up, Deliver, Confirm).
 * Prevents accidental taps while in vehicle cradle or in motion.
 * Features spring snap-back physics and haptic trigger.
 */
@Composable
fun SwipeToActionButton(
    text: String,
    icon: ImageVector = Icons.Filled.Check,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    onSwipeComplete: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    val colors = LocalDriversColors.current
    val accent = accentColor ?: colors.purple
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val trackHeight = 52.dp
    val thumbSize = 44.dp
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(FluidShapes.button)
            .background(accent.copy(alpha = 0.12f))
    ) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        val thumbSizePx = with(density) { thumbSize.toPx() }
        val maxDragPx = (totalWidthPx - thumbSizePx - with(density) { 8.dp.toPx() }).coerceAtLeast(0f)

        val offsetX = remember { Animatable(0f) }

        val isCompleted = offsetX.value >= maxDragPx * 0.85f

        // Centered guiding action text
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = accent
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = text,
                        color = accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        null,
                        tint = accent.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Draggable thumb pill
        if (!isLoading && enabled) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.value.roundToInt() + with(density) { 4.dp.roundToPx() }, with(density) { 4.dp.roundToPx() }) }
                .size(thumbSize)
                .clip(FluidShapes.pill)
                .background(accent)
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            coroutineScope.launch {
                                val next = (offsetX.value + delta).coerceIn(0f, maxDragPx)
                                offsetX.snapTo(next)
                            }
                        },
                        onDragStopped = {
                            if (offsetX.value >= maxDragPx * 0.8f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coroutineScope.launch {
                                    offsetX.animateTo(
                                        targetValue = maxDragPx,
                                        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)
                                    )
                                    onSwipeComplete()
                                    // Reset after trigger
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)
                                    )
                                }
                            } else {
                                coroutineScope.launch {
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)
                                    )
                                }
                            }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Modern floating Home pill button with icon and label, used on sub-pages (e.g. Completed Packages, Trip History)
 * to quickly navigate back to the primary Home feed.
 */
@Composable
fun FloatingHomeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Home"
) {
    val colors = LocalDriversColors.current

    Surface(
        modifier = modifier
            .fluidBounceClick(scaleDown = 0.92f) { onClick() },
        shape = FluidShapes.pill,
        color = colors.purple,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.Home,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

// ── Full-screen Home Skeleton UI (Replaces Circular Loading Spinners) ─────────

@Composable
fun HomeScreenSkeleton(
    modifier: Modifier = Modifier
) {
    val colors = LocalDriversColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "homeSkeletonShimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "homeSkeletonAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header bar skeleton
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(colors.divider.copy(alpha = alpha))
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(width = 120.dp, height = 16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.divider.copy(alpha = alpha))
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 75.dp, height = 11.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.divider.copy(alpha = alpha * 0.7f))
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.divider.copy(alpha = alpha))
                )
            }

            Spacer(Modifier.height(6.dp))

            // Hero / Active banner skeleton
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                shape = FluidShapes.card,
                color = colors.surface.copy(alpha = alpha),
                border = BorderStroke(1.dp, colors.divider.copy(alpha = alpha))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 90.dp, height = 14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.textSecondary.copy(alpha = 0.2f))
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 50.dp, height = 18.dp)
                                .clip(FluidShapes.pill)
                                .background(colors.purple.copy(alpha = 0.25f))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.textSecondary.copy(alpha = 0.2f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.textSecondary.copy(alpha = 0.15f))
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Package / item card skeletons
            repeat(3) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = FluidShapes.card,
                    color = colors.surface.copy(alpha = alpha),
                    border = BorderStroke(1.dp, colors.divider.copy(alpha = alpha))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colors.divider.copy(alpha = alpha))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.textSecondary.copy(alpha = 0.2f))
                            )
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.4f)
                                    .height(11.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.textSecondary.copy(alpha = 0.15f))
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(width = 60.dp, height = 22.dp)
                                .clip(FluidShapes.pill)
                                .background(colors.divider.copy(alpha = alpha))
                        )
                    }
                }
            }
        }

        // Bottom dock skeleton pill
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.85f)
                .height(58.dp)
                .clip(FluidShapes.dock)
                .background(colors.surface.copy(alpha = alpha))
                .border(1.dp, colors.divider.copy(alpha = alpha), FluidShapes.dock)
        )
    }
}

// ── Public Package Search Skeleton (for pre-login track loading) ──────────────

@Composable
fun PublicPackageSkeleton(
    alpha: Float,
    modifier: Modifier = Modifier
) {
    val colors = LocalDriversColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceAlt.copy(alpha = alpha),
        border = BorderStroke(1.dp, colors.divider.copy(alpha = alpha))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 110.dp, height = 15.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.textSecondary.copy(alpha = 0.2f))
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(width = 70.dp, height = 22.dp)
                        .clip(FluidShapes.pill)
                        .background(colors.purple.copy(alpha = 0.25f))
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.textSecondary.copy(alpha = 0.25f))
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.textSecondary.copy(alpha = 0.15f))
            )
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.purple.copy(alpha = 0.2f))
            )
        }
    }
}


