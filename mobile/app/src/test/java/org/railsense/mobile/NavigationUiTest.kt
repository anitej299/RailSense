package org.railsense.mobile

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Real navigation/composables against host Android resources; not a physical-device test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun screenshot(name: String) {
        ui.waitForIdle()
        val file=File("build/reports/ui/$name.png").apply{parentFile?.mkdirs()}
        ui.runOnIdle {
            val view=ui.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }

    @Test fun bottomNavigationTripEventAndExportRemainConnected() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val dao=Store.db(context).trips()
        runBlocking {
            (context.applicationContext as RailSenseApp).ready.await()
            dao.insert(TripEntity("ui-test-trip",1000,5000,"Test device","Test sensors","baseline","SYNTHETIC_TEST","STOPPED",false,200,0,0,"test"))
            dao.save(EventEntity("ui-test-event","ui-test-trip",2000,4000,.9,.8,2,null,null,null,null,"GOOD","baseline","1,2,3,4,5,6,7","1,2,3,2,1"))
        }
        ui.onNodeWithText("RailSense").assertIsDisplayed()
        screenshot("home")
        ui.onNodeWithText("Map",useUnmergedTree=true).performClick()
        ui.onNodeWithText("Anomaly map").assertIsDisplayed()
        screenshot("map")
        ui.onNodeWithText("Settings",useUnmergedTree=true).performClick()
        ui.onNodeWithText("Sensor configuration").assertIsDisplayed()
        screenshot("settings")
        ui.onNodeWithText("History",useUnmergedTree=true).performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("View trip details").fetchSemanticsNodes().isNotEmpty() }
        screenshot("history")
        ui.onNodeWithText("View trip details").performClick()
        ui.onNodeWithText("Trip details").assertIsDisplayed()
        ui.onNodeWithText("Export & share").performScrollTo().performClick()
        ui.onNodeWithText("Your data. Your choice.").assertIsDisplayed()
        screenshot("export")
        ui.onNodeWithContentDescription("Back").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Potential vibration anomaly").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Potential vibration anomaly").performScrollTo().performClick()
        ui.onNodeWithText("Event details").assertIsDisplayed()
        screenshot("event")
        ui.onNodeWithText("Acknowledge event").performScrollTo().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Acknowledged").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Home",useUnmergedTree=true).performClick()
        ui.onNodeWithText("RailSense").assertIsDisplayed()
        ui.onNodeWithText("Home",useUnmergedTree=true).performClick()
        ui.onNodeWithText("Monitoring inactive").assertIsDisplayed()
        runBlocking { dao.delete("ui-test-trip") }
    }
}
