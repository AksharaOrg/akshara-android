package org.slashboard.ime.update

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UpdateCheckTest {

    private lateinit var context: Context
    private lateinit var updateManager: UpdateManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        updateManager = UpdateManager(context)
    }

    @Test
    fun testIsNewerVersion() {
        assertTrue("v2.0.0 is newer than v1.0.0", updateManager.isNewerVersion("2.0.0", "1.0.0"))
        assertTrue("v1.1.0 is newer than v1.0.9", updateManager.isNewerVersion("1.1.0", "1.0.9"))
        assertTrue("v1.0.1 is newer than v1.0.0", updateManager.isNewerVersion("1.0.1", "1.0.0"))
        assertFalse("v1.0.0 is not newer than v1.0.0", updateManager.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse("v1.0.0 is not newer than v2.0.0", updateManager.isNewerVersion("1.0.0", "2.0.0"))
        assertFalse("v1.0.0 is not newer than v1.0.1", updateManager.isNewerVersion("1.0.0", "1.0.1"))
    }

    @Test
    fun testNotificationDisplayWhenUpdateAvailable() {
        val info = UpdateInfo(
            hasUpdate = true,
            latestVersion = "v2.5.0",
            downloadUrl = "https://example.com/slashboard.apk",
            releaseNotes = "Major performance upgrade and new fonts"
        )

        UpdateCheckWorker.showNotification(context, info)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = shadowOf(notificationManager)
        val notifications = shadowNm.allNotifications

        assertTrue("Expected at least 1 notification to be posted", notifications.isNotEmpty())
        val updateNotif = notifications.firstOrNull { it.extras.getCharSequence("android.title")?.toString()?.contains("New update available", ignoreCase = true) == true }
        assertNotNull("Expected notification with title 'New update available'", updateNotif)

        val title = updateNotif!!.extras.getCharSequence("android.title")?.toString()
        assertEquals("New update available", title)

        val text = updateNotif.extras.getCharSequence("android.text")?.toString()
        assertTrue("Expected text to contain version v2.5.0", text?.contains("v2.5.0") == true)
    }
}
