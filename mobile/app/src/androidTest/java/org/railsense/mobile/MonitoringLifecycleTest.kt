package org.railsense.mobile

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonitoringLifecycleTest {
    @Test fun demonstrationSurvivesActivityRecreationAndStopsCleanly() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        Preferences(context).save(MonitorConfig(gps=false,notifications=false))
        val activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            val start=Intent(context,MonitoringService::class.java).putExtra("demo",true).putExtra("gps",false)
            ContextCompat.startForegroundService(context,start)
            withTimeout(10_000) { while(MonitorState.live.value.tripId==null || !MonitorState.live.value.running)delay(100) }
            val trip=MonitorState.live.value.tripId!!
            ContextCompat.startForegroundService(context,start) // must not duplicate trip/subscription
            instrumentation.runOnMainSync { activity.recreate() }
            withTimeout(25_000) { while(MonitorState.live.value.eventCount==0)delay(200) }
            assertEquals(trip,MonitorState.live.value.tripId)
            assertEquals("SYNTHETIC_TEST",MonitorState.live.value.source)
            assertNull(MonitorState.live.value.sample?.fix)
            context.startService(Intent(context,MonitoringService::class.java).setAction("STOP"))
            withTimeout(10_000) { while(MonitorState.live.value.running)delay(100) }
            assertEquals("STOPPED",Store.db(context).trips().trip(trip)!!.status)
            assertEquals(1,Store.db(context).trips().events(trip).size)
        } finally {
            context.startService(Intent(context,MonitoringService::class.java).setAction("STOP"))
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
