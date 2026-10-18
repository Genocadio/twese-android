package com.gocavgo.twese.ui.driver

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gocavgo.twese.ui.common.AcrylicGlassSurface
import com.gocavgo.twese.ui.common.FluidShapes
import com.gocavgo.twese.ui.common.fluidBounceClick
import com.gocavgo.twese.ui.theme.LocalDriversColors
import com.gocavgo.twese.viewmodel.TripViewModel
import java.util.Locale

@Composable
fun FloatingTripOverlay(
    viewModel: TripViewModel,
    modifier: Modifier = Modifier,
    onOpenCreateTrip: () -> Unit = { viewModel.openDriverCreateTrip() }
) {
    val state by viewModel.state.collectAsState()
    val colors = LocalDriversColors.current

    val activeTrip = state.activeDriverTrip
    val hasActiveTrip = state.hasActiveTrip || activeTrip != null

    var isExpanded by remember { mutableStateOf(false) }

    // Pulsing animation for live status dot
    val infiniteTransition = rememberInfiniteTransition(label = "tripPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Compute route label and stop details
    val routeLabel = activeTrip?.routeName
        ?: (if (activeTrip?.origin != null && activeTrip.destination != null) "${activeTrip.origin} → ${activeTrip.destination}" else null)
        ?: state.trip.routeLabel.ifBlank { "Active Route" }

    val activeWp = activeTrip?.waypoints?.firstOrNull { it.isNext }
        ?: activeTrip?.waypoints?.firstOrNull { !it.isPassed }
        ?: activeTrip?.waypoints?.getOrNull(state.currentStopIndex)

    val activeStopIndex = if (activeWp != null && activeTrip?.waypoints != null) {
        activeTrip.waypoints.indexOf(activeWp).coerceAtLeast(0)
    } else {
        state.currentStopIndex
    }

    val stop = state.trip.stops.getOrNull(activeStopIndex) ?: viewModel.currentStop
    val totalStops = state.trip.stops.size.coerceAtLeast(activeTrip?.waypoints?.size ?: 0)
    val stopCountText = if (totalStops > 0) "${(activeStopIndex + 1).coerceAtMost(totalStops)}/$totalStops" else "1/1"

    val distMeters = activeWp?.remainingDistance?.takeIf { it > 0.0 }
        ?: activeTrip?.remainingDistanceToDestination?.takeIf { it > 0.0 }
    val distFormatted = distMeters?.let { dist ->
        if (dist < 1000) "${dist.toInt()} m"
        else "${String.format(Locale.US, "%.1f", dist / 1000.0)} km"
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End
    ) {
        // ── Expanded Trip HUD Card ─────────────────────────────────────────
        AnimatedVisibility(
            visible = isExpanded && hasActiveTrip,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.75f)) +
                    slideInVertically(spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.75f)) { it / 2 } +
                    scaleIn(spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.75f), initialScale = 0.85f),
            exit = fadeOut(tween(180)) +
                    slideOutVertically(tween(180)) { it / 2 } +
                    scaleOut(tween(180), targetScale = 0.85f)
        ) {
            AcrylicGlassSurface(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .padding(bottom = 12.dp),
                shape = FluidShapes.card,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, colors.purple.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Header: Live dot + Route Name + Minimize button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.purple.copy(alpha = pulseAlpha))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            routeLabel,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { isExpanded = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Minimize trip overlay",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = colors.divider.copy(alpha = 0.6f))
                    Spacer(Modifier.height(12.dp))

                    // Next / Current Stop Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(colors.purple.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Navigation,
                                    contentDescription = null,
                                    tint = colors.purple,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    "NEXT STOP • $stopCountText",
                                    color = colors.purple,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    stop.name.ifBlank { "Destination" },
                                    color = colors.textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (!distFormatted.isNullOrBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = FluidShapes.chip,
                                color = colors.purple.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, colors.purple.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.NearMe,
                                        contentDescription = null,
                                        tint = colors.purple,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        distFormatted,
                                        color = colors.purple,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (stop.address.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stop.address,
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 42.dp)
                        )
                    }

                    // Package stats at stop
                    val pickupCount = stop.pickups.size
                    val dropoffCount = stop.dropoffs.size
                    if (pickupCount > 0 || dropoffCount > 0) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(FluidShapes.button)
                                .background(colors.surfaceAlt.copy(alpha = 0.6f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AddCircle, null, tint = colors.purple, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("$pickupCount Pickups", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Box(Modifier.width(1.dp).height(14.dp).background(colors.divider))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.RemoveCircle, null, tint = colors.red, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("$dropoffCount Drop-offs", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // ── Minimized Floating Bubble (Sitting right above the [+] FAB) ────
        if (hasActiveTrip) {
            AcrylicGlassSurface(
                modifier = Modifier
                    .fluidBounceClick(scaleDown = 0.92f) { isExpanded = !isExpanded },
                shape = FluidShapes.capsule,
                shadowElevation = 5.dp,
                border = BorderStroke(1.dp, colors.purple.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pulsing live indicator dot
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(colors.purple.copy(alpha = pulseAlpha))
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.DirectionsCar,
                        contentDescription = "Active Trip",
                        tint = colors.purple,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stopCountText,
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!distFormatted.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "• $distFormatted",
                            color = colors.purple,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            // Subtle "Start Trip" pill when no trip is active
            AcrylicGlassSurface(
                modifier = Modifier
                    .fluidBounceClick(scaleDown = 0.94f) { onOpenCreateTrip() },
                shape = FluidShapes.capsule,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, colors.divider.copy(alpha = 0.8f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.DirectionsCar,
                        contentDescription = "Start Trip",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Start Trip",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
