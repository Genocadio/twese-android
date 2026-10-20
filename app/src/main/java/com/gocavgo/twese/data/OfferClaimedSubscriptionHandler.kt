package com.gocavgo.twese.data

import android.util.Log
import com.gocavgo.twese.OfferClaimedSubscription
import com.gocavgo.twese.network.ApolloClientProvider
import com.gocavgo.twese.nexx.NexxAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Subscribes to the `offerClaimed` GraphQL subscription and emits
 * [OfferClaimedEvent]s in real-time whenever an offer is claimed/accepted
 * by any driver or worker.
 */
object OfferClaimedSubscriptionHandler {

    private const val TAG = "OfferClaimedSub"
    private const val INITIAL_BACKOFF_MS = 1_000L
    private const val MAX_BACKOFF_MS = 60_000L
    private const val BACKOFF_FACTOR = 2

    data class OfferClaimedEvent(
        val packageId: String,
        val trackingCode: String,
        val transferId: String?,
        val claimedByUserId: String,
        val claimedByRole: String,
        val claimedAt: String
    )

    private val _events = MutableSharedFlow<OfferClaimedEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<OfferClaimedEvent> = _events.asSharedFlow()

    private var subscriptionJob: Job? = null
    private var started = false

    fun start(scope: CoroutineScope) {
        if (started) return
        started = true

        subscriptionJob = scope.launch { runSubscriptionWithRetry() }
        Log.d(TAG, "OfferClaimedSubscriptionHandler started")
    }

    fun stop() {
        started = false
        subscriptionJob?.cancel()
        subscriptionJob = null
    }

    private suspend fun runSubscriptionWithRetry() {
        var backoffMs = INITIAL_BACKOFF_MS

        while (started) {
            try {
                // Proactive token refresh
                try {
                    val exp = getJwtExpirySeconds()
                    val now = System.currentTimeMillis() / 1000
                    if (exp == null || exp - now < 5 * 60) {
                        Log.d(TAG, "proactive refresh before reconnect (exp=$exp, now=$now)")
                        NexxAuth.refreshSession()
                        ApolloClientProvider.resetClient()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "proactive refresh failed: ${e.message}")
                }

                Log.d(TAG, "connecting offerClaimed subscription...")
                ApolloClientProvider.client
                    .subscription(OfferClaimedSubscription())
                    .toFlow()
                    .collect { response ->
                        backoffMs = INITIAL_BACKOFF_MS
                        if (!started) return@collect
                        val gqlErrors = response.errors
                        if (!gqlErrors.isNullOrEmpty()) {
                            Log.e(TAG, "subscription errors: ${gqlErrors.joinToString { it.message }}")
                            return@collect
                        }
                        val gql = response.data?.offerClaimed ?: return@collect
                        val event = OfferClaimedEvent(
                            packageId = gql.packageId,
                            trackingCode = gql.trackingCode,
                            transferId = gql.transferId,
                            claimedByUserId = gql.claimedByUserId,
                            claimedByRole = gql.claimedByRole,
                            claimedAt = gql.claimedAt
                        )
                        _events.emit(event)
                        Log.d(TAG, "offerClaimed received: pkg=${event.trackingCode}, transfer=${event.transferId}")
                    }
                Log.w(TAG, "subscription stream ended, reconnecting in ${backoffMs}ms")
            } catch (e: Exception) {
                Log.w(TAG, "subscription error: ${e.message}, reconnecting in ${backoffMs}ms")
            }

            if (!started) break
            delay(backoffMs)
            backoffMs = (backoffMs * BACKOFF_FACTOR).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private fun getJwtExpirySeconds(): Long? {
        val token = NexxAuth.getAccessToken() ?: return null
        val parts = token.split(".")
        if (parts.size < 2) return null
        return try {
            val payload = String(
                android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE),
                Charsets.UTF_8
            )
            val json = org.json.JSONObject(payload)
            if (json.has("exp")) json.getLong("exp") else null
        } catch (e: Exception) {
            null
        }
    }
}
