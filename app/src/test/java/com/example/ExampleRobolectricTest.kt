package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.octastream.data.PreferenceManager
import com.octastream.logger.AppLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `verify app name and core telemetry modules`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("octa", appName)

        val prefManager = PreferenceManager.getInstance(context)
        prefManager.setThreadCount(8)
        assertEquals(8, prefManager.settings.value.threadCount)

        AppLogger.info("UnitTest", "Verification log entry")
        assertTrue(AppLogger.logs.value.any { it.message.contains("Verification log entry") })
    }
}
