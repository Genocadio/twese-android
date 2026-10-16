package com.gocavgo.twese.ui.common

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.gocavgo.twese.cache.MediaCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Data types ────────────────────────────────────────────────────────────────

data class MediaCacheState(
    val uri: String,
    val isCached: Boolean,
    val isFailed: Boolean,
    val source: MediaSource
)

enum class MediaSource { CACHED, STREAMING, LOADING, FAILED }

/**
 * Returns true if the media URL represents a video format (e.g. mp4, mov, webm).
 * Strips query parameters and fragments so signed URLs are correctly identified.
 */
fun isVideoUrl(url: String): Boolean {
    val clean = url.substringBefore('?').substringBefore('#').lowercase()
    return clean.endsWith(".mp4") || clean.endsWith(".mov") || clean.endsWith(".webm") || clean.endsWith(".mkv")
}

// ── Cache-state composable helper ─────────────────────────────────────────────

@Composable
fun mediaCacheState(url: String): MediaCacheState {
    val context    = LocalContext.current
    val cache      = remember { MediaCache.getInstance(context) }

    // Reactive state so when caching completes, the composable reactively updates to the local file
    var cachedFile by remember(url) { mutableStateOf(cache.getCachedFile(url)) }
    var isFailed   by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (url.isBlank()) return@LaunchedEffect
        val existing = cache.getCachedFile(url)
        if (existing != null) {
            cachedFile = existing
            isFailed = false
            return@LaunchedEffect
        }
        try {
            val file = cache.cacheMedia(url)
            cachedFile = file
            isFailed = false
            Log.d("MediaComponents", "DOWNLOAD_DONE $url")
        } catch (e: Exception) {
            isFailed = true
            Log.d("MediaComponents", "DOWNLOAD_FAIL $url → ${e.message}")
        }
    }

    val localUri = cachedFile?.let { Uri.fromFile(it).toString() }
    val isCached = cachedFile != null
    val source = when {
        isCached -> MediaSource.CACHED
        isFailed -> MediaSource.FAILED
        else     -> MediaSource.STREAMING
    }

    return MediaCacheState(uri = localUri ?: url, isCached = isCached, isFailed = isFailed, source = source)
}

// ── Media Carousel ────────────────────────────────────────────────────────────

@Composable
fun MediaCarousel(mediaUrls: List<String>, modifier: Modifier = Modifier, onMaximize: (() -> Unit)? = null) {
    if (mediaUrls.isEmpty()) return
    val colors     = com.gocavgo.twese.ui.theme.LocalDriversColors.current
    val pagerState = rememberPagerState(pageCount = { mediaUrls.size })
    val scope      = rememberCoroutineScope()
    val context    = LocalContext.current
    val cache      = remember { MediaCache.getInstance(context) }

    LaunchedEffect(mediaUrls) { cache.enqueuePreload(mediaUrls, scope) }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(300.dp)) { page ->
                val url        = mediaUrls[page]
                val mediaState = mediaCacheState(url)
                val isVideo    = isVideoUrl(url)

                Box(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceAlt).border(1.dp, colors.divider, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isVideo) {
                        VideoPlayer(mediaState.uri, useController = false, shutterColorHex = colors.surfaceAlt)
                    } else {
                        var retryKey by remember(url) { mutableIntStateOf(0) }
                        var isImageError by remember(url, retryKey) { mutableStateOf(false) }

                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(mediaState.uri)
                                    .setParameter("retry", retryKey, null)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Package photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                loading = {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(
                                            color = colors.blue,
                                            modifier = Modifier.size(32.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                },
                                onError = {
                                    isImageError = true
                                },
                                onSuccess = {
                                    isImageError = false
                                }
                            )

                            if (isImageError) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(colors.surfaceAlt.copy(alpha = 0.9f))
                                        .clickable {
                                            isImageError = false
                                            retryKey++
                                        }
                                        .padding(16.dp)
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Retry", tint = colors.blue, modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.height(6.dp))
                                    Text("Tap to reload image", color = colors.textPrimary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (mediaUrls.size > 1) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    repeat(mediaUrls.size) { i ->
                        Box(
                            modifier = Modifier.padding(horizontal = 3.dp)
                                .size(if (pagerState.currentPage == i) 8.dp else 6.dp).clip(CircleShape)
                                .background(if (pagerState.currentPage == i) colors.blue else colors.divider)
                        )
                    }
                }
            }
        }

        if (onMaximize != null) {
            IconButton(
                onClick = onMaximize,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp)
            ) {
                Icon(Icons.Filled.Fullscreen, contentDescription = "Maximize", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Full-Screen Media Viewer ──────────────────────────────────────────────────

@Composable
fun FullScreenMediaViewer(
    mediaUrls: List<String>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    showClose: Boolean = true
) {
    if (mediaUrls.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { mediaUrls.size })
    val scope      = rememberCoroutineScope()
    val context    = LocalContext.current
    val cache      = remember { MediaCache.getInstance(context) }

    LaunchedEffect(mediaUrls) { cache.enqueuePreload(mediaUrls, scope) }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val url        = mediaUrls[page]
            val mediaState = mediaCacheState(url)
            val isVideo    = isVideoUrl(url)

            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF2A2A2A)), contentAlignment = Alignment.Center) {
                if (isVideo) {
                    VideoPlayer(mediaState.uri, useController = true, shutterColorHex = Color(0xFF2A2A2A))
                } else {
                    var scale   by remember { mutableStateOf(1f) }
                    var offsetX by remember { mutableStateOf(0f) }
                    var offsetY by remember { mutableStateOf(0f) }
                    var retryKey by remember(url) { mutableIntStateOf(0) }
                    var isImageError by remember(url, retryKey) { mutableStateOf(false) }

                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(mediaState.uri)
                                .setParameter("retry", retryKey, null)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            },
                            contentScale = ContentScale.Fit,
                            loading = {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF90CAF9),
                                        modifier = Modifier.size(40.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                }
                            },
                            onError = {
                                isImageError = true
                            },
                            onSuccess = {
                                isImageError = false
                            }
                        )

                        if (isImageError) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.7f))
                                    .clickable {
                                        isImageError = false
                                        retryKey++
                                    }
                                    .padding(16.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Retry", tint = Color(0xFF90CAF9), modifier = Modifier.size(36.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Tap to reload image", color = Color.White, fontSize = 13.sp)
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event       = awaitPointerEvent()
                                    val changes     = event.changes
                                    val pointerCount = changes.count { it.pressed }
                                    if (pointerCount >= 2) {
                                        val c0 = changes[0]; val c1 = changes[1]
                                        val prevDist = (c0.previousPosition - c1.previousPosition).getDistance()
                                        val currDist = (c0.position - c1.position).getDistance()
                                        val zoom     = if (prevDist > 0f) currDist / prevDist else 1f
                                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                                        scale = newScale
                                        if (newScale > 1f) {
                                            val center     = (c0.position + c1.position) / 2f
                                            val prevCenter = (c0.previousPosition + c1.previousPosition) / 2f
                                            offsetX += center.x - prevCenter.x
                                            offsetY += center.y - prevCenter.y
                                        }
                                        changes.forEach { it.consume() }
                                    } else if (scale > 1.01f) {
                                        val change = changes.first()
                                        val delta  = change.position - change.previousPosition
                                        offsetX += delta.x; offsetY += delta.y
                                        change.consume()
                                    }
                                } while (changes.any { it.pressed })
                            }
                        })
                    }
                }
            }
        }

        // Close button
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(36.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(if (showClose) Icons.Filled.Close else Icons.Filled.FullscreenExit, contentDescription = null, tint = Color.White)
        }

        // Bottom thumbnail strip
        if (mediaUrls.size > 1) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(mediaUrls.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (pagerState.currentPage == index) 44.dp else 36.dp)
                            .clip(RoundedCornerShape(6.dp)).background(Color.DarkGray)
                            .border(2.dp, if (pagerState.currentPage == index) Color.White else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { scope.launch { pagerState.animateScrollToPage(index) } },
                        contentAlignment = Alignment.Center
                    ) {
                        val thumbUrl = mediaUrls[index]
                        val isVid    = isVideoUrl(thumbUrl)
                        if (isVid) {
                            val cachedFile = remember(thumbUrl) { cache.getCachedFile(thumbUrl) }
                            var frameBitmap by remember { mutableStateOf<Bitmap?>(null) }
                            if (cachedFile != null) {
                                LaunchedEffect(cachedFile) {
                                    withContext(Dispatchers.IO) {
                                        val retriever = MediaMetadataRetriever()
                                        try {
                                            retriever.setDataSource(cachedFile.absolutePath)
                                            frameBitmap = retriever.getFrameAtTime(100 * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                                        } catch (_: Exception) {} finally { retriever.release() }
                                    }
                                }
                            }
                            val fb = frameBitmap
                            if (fb != null) {
                                Image(bitmap = fb.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Filled.PlayArrow, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                            }
                        } else {
                            AsyncImage(model = thumbUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                }
            }
        }
    }
}

// ── Private helpers ───────────────────────────────────────────────────────────

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun VideoPlayer(uri: String, useController: Boolean, shutterColorHex: Color) {
    val ctx = LocalContext.current
    val shutterInt = android.graphics.Color.rgb(
        (shutterColorHex.red * 255).toInt(),
        (shutterColorHex.green * 255).toInt(),
        (shutterColorHex.blue * 255).toInt()
    )

    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var retryCount by remember(uri) { mutableIntStateOf(0) }

    val currentPlayerRef = remember { mutableStateOf<ExoPlayer?>(null) }

    remember(uri, retryCount) {
        currentPlayerRef.value?.release()
        playbackError = null
        isBuffering = true
        try {
            ExoPlayer.Builder(ctx).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering = state == Player.STATE_BUFFERING
                        Log.d("MediaComponents", "Player state=$state uri=${uri.take(40)}")
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.w("MediaComponents", "Player error for $uri: ${error.message}")
                        playbackError = error.message
                        isBuffering = false
                    }
                })
            }.also { currentPlayerRef.value = it }
        } catch (e: Exception) {
            playbackError = e.message
            isBuffering = false
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose { currentPlayerRef.value?.release() }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = currentPlayerRef.value
                    this.useController = useController
                    setShutterBackgroundColor(shutterInt)
                }
            },
            update = { view ->
                view.player = currentPlayerRef.value
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering && playbackError == null) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(36.dp),
                strokeWidth = 2.5.dp
            )
        }

        if (playbackError != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable { retryCount++ }
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Retry", tint = Color(0xFF90CAF9), modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(6.dp))
                Text("Tap to reload video", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}
