package com.gocavgo.twese.ui.driver

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gocavgo.twese.ui.common.FloatingHomeButton
import com.gocavgo.twese.ui.common.FluidShapes
import com.gocavgo.twese.ui.common.contentMaxWidth
import com.gocavgo.twese.ui.common.isWideScreen
import com.gocavgo.twese.ui.theme.LocalDriversColors
import com.gocavgo.twese.viewmodel.CompletedTrip

/**
 * Full page Driver Trip History Screen:
 * Displays all completed driver trips with route details, plate numbers, and a floating Home button.
 */
@Composable
fun DriverTripHistoryScreen(
    trips: List<CompletedTrip>,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDriversColors.current
    val wide = isWideScreen()
    val maxW = contentMaxWidth()

    BackHandler { onHomeClick() }

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
            // ── Top Header ───────────────────────────────────────────────────
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
                    "Trip History",
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
                        "${trips.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.purple,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            // ── Trips List or Empty State ─────────────────────────────────────
            if (trips.isEmpty()) {
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
                                    Icons.Filled.History,
                                    null,
                                    tint = colors.purple,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "No completed trips yet",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Trips you complete will be archived here for easy reference.",
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
                    items(trips) { trip ->
                        Surface(
                            shape = FluidShapes.card,
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.divider),
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(colors.purple.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        null,
                                        tint = colors.purple,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${trip.origin} → ${trip.destination}",
                                        color = colors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.DirectionsCar,
                                            null,
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            trip.plateNumber,
                                            color = colors.textSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.purple.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        "Completed",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        color = colors.purple,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Floating Home Button (Floating at bottom center) ──────────────────
        FloatingHomeButton(
            onClick = onHomeClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        )
    }
}

@Composable
fun CompletedTripsHistorySection(trips: List<CompletedTrip>, modifier: Modifier = Modifier) {
    val colors = LocalDriversColors.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.History, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Completed Trips", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Spacer(Modifier.weight(1f))
            Text("${trips.size}", color = colors.textSecondary, fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))
        trips.forEachIndexed { index, trip ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.divider),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.purple.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = colors.purple, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${trip.origin} → ${trip.destination}",
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.DirectionsCar, null, tint = colors.textSecondary, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(trip.plateNumber, color = colors.textSecondary, fontSize = 12.sp)
                        }
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = colors.purple.copy(alpha = 0.1f)) {
                        Text("Done", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = colors.purple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (index < trips.lastIndex) Spacer(Modifier.height(8.dp))
        }
    }
}
