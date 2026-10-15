package com.gocavgo.twese

import com.gocavgo.twese.data.Notice
import com.gocavgo.twese.data.NoticeRepository
import com.gocavgo.twese.data.PackageRepository
import com.gocavgo.twese.service.MqttLocationPublisher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeltaSyncAndAdaptiveGpsTest {

    @Before
    fun setUp() {
        PackageRepository.lastSyncTimestamp = null
    }

    @Test
    fun updateLastSyncTimestamp_tracksMonotonicallyIncreasingTimestamps() {
        // Initial set
        PackageRepository.updateLastSyncTimestamp("2026-10-15T12:00:00Z")
        assertEquals("2026-10-15T12:00:00Z", PackageRepository.lastSyncTimestamp)

        // Monotonic advance
        PackageRepository.updateLastSyncTimestamp("2026-10-15T13:00:00Z")
        assertEquals("2026-10-15T13:00:00Z", PackageRepository.lastSyncTimestamp)

        // Older timestamp should not regress state
        PackageRepository.updateLastSyncTimestamp("2026-10-15T11:00:00Z")
        assertEquals("2026-10-15T13:00:00Z", PackageRepository.lastSyncTimestamp)

        // Blank or null ignored
        PackageRepository.updateLastSyncTimestamp("")
        PackageRepository.updateLastSyncTimestamp(null)
        assertEquals("2026-10-15T13:00:00Z", PackageRepository.lastSyncTimestamp)
    }

    @Test
    fun ingestSyncNotices_deduplicatesAndUpdatesUnreadCount() {
        val notice1 = Notice(
            id = "notif-1",
            resourceType = "PACKAGE",
            resourceId = "pkg-1",
            eventType = "PACKAGE_DELIVERED",
            actorId = "101",
            title = "Delivered",
            message = "Package delivered",
            payload = null,
            viewerId = "viewer-1",
            viewerNoticeId = "notif-1",
            viewerReadAt = null,
            createdAt = "2026-10-15T10:00:00Z"
        )
        val notice2 = Notice(
            id = "notif-2",
            resourceType = "TRANSFER",
            resourceId = "trf-1",
            eventType = "TRANSFER_PENDING",
            actorId = "102",
            title = "Transfer Request",
            message = "Transfer offer",
            payload = null,
            viewerId = "viewer-2",
            viewerNoticeId = "notif-2",
            viewerReadAt = "2026-10-15T10:30:00Z",
            createdAt = "2026-10-15T10:25:00Z"
        )

        // First ingestion
        NoticeRepository.ingestSyncNotices(listOf(notice1, notice2))
        assertEquals(2, NoticeRepository.notices.value.size)
        assertEquals(1, NoticeRepository.unreadCount.value)

        // Ingest duplicate notice1 plus new notice3
        val notice3 = Notice(
            id = "notif-3",
            resourceType = "PACKAGE",
            resourceId = "pkg-3",
            eventType = "PACKAGE_PICKED_UP",
            actorId = "103",
            title = "Picked up",
            message = "Package picked up",
            payload = null,
            viewerId = "viewer-3",
            viewerNoticeId = "notif-3",
            viewerReadAt = null,
            createdAt = "2026-10-15T11:00:00Z"
        )
        NoticeRepository.ingestSyncNotices(listOf(notice1, notice3))
        assertEquals(3, NoticeRepository.notices.value.size)
        assertEquals(2, NoticeRepository.unreadCount.value)
    }

    @Test
    fun mqttLocationPublisher_setStationaryMode_togglesState() {
        assertFalse(MqttLocationPublisher.isStationary)

        MqttLocationPublisher.setStationaryMode(true)
        assertTrue(MqttLocationPublisher.isStationary)

        MqttLocationPublisher.setStationaryMode(false)
        assertFalse(MqttLocationPublisher.isStationary)
    }
}
