package info.yuryv.androiddebugmode.screenshot

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WidgetScreenshotTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun placeWidgetAndCaptureScreenshot() {
        pressHomeAndWait()
        openWidgetPicker()
        placeWidget()
        pressHomeAndWait()
        waitForGlanceRender()
        takeScreenshot()
    }

    // ---------------------------------------------------------------------------
    // Steps
    // ---------------------------------------------------------------------------

    private fun pressHomeAndWait() {
        device.pressHome()
        device.waitForIdle(3_000)
    }

    private fun openWidgetPicker() {
        longPressHomeScreen()
        dumpHierarchy("after_long_press")

        val widgetsBtn = device.wait(Until.findObject(By.text("Widgets")), 5_000)
            ?: error("'Widgets' button not found after long-pressing the home screen")
        widgetsBtn.click()
        device.waitForIdle(2_000)
        dumpHierarchy("after_widgets_click")
    }

    private fun placeWidget() {
        // Launcher3 groups the picker by app. The app section header has the app name;
        // tapping it may expand or it may already show the widget tile inline.
        val appHeader = device.wait(
            Until.findObject(By.textContains("Debug Mode")),
            5_000,
        ) ?: error("App section 'Debug Mode' not found in widget picker")

        dumpHierarchy("found_app_header")
        appHeader.click()
        device.waitForIdle(1_500)
        dumpHierarchy("after_app_header_click")

        // The draggable widget tile is a sibling or child near the header.
        // Try several selector strategies in order of specificity.
        val widgetTile =
            device.wait(Until.findObject(By.descContains("Debug Mode Widget")), 2_000)
                ?: device.findObject(By.textContains("Debug Mode Widget"))
                ?: device.findObject(By.textContains("Debug Mode").pkg("com.android.launcher3"))
                ?: error("Widget tile not found in picker after expanding app section")

        dumpHierarchy("found_widget_tile")

        val bounds = widgetTile.visibleBounds
        val dropX = device.displayWidth / 2
        val dropY = device.displayHeight / 3
        device.drag(bounds.centerX(), bounds.centerY(), dropX, dropY, 60)
        device.waitForIdle(2_000)
        dumpHierarchy("after_drag")
    }

    private fun waitForGlanceRender() {
        // Glance pushes RemoteViews asynchronously; wait for initial composition.
        Thread.sleep(3_000)
    }

    private fun takeScreenshot() {
        val out = File("/sdcard/Pictures/01_widget.png")
        out.parentFile?.mkdirs()
        device.takeScreenshot(out, 1.0f, 100)
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Simulates a long press at the centre of the home screen via `input touchscreen swipe`.
     * A zero-distance swipe held for 2 s reliably triggers the long-press gesture, unlike
     * UiObject2.longClick() which depends on finding the right container element.
     */
    private fun longPressHomeScreen() {
        val x = device.displayWidth / 2
        val y = device.displayHeight / 3
        shell("input touchscreen swipe $x $y $x $y 2000")
        device.waitForIdle(3_000)
    }

    /** Dumps the current window hierarchy to /sdcard/Pictures/ for CI artifact upload. */
    private fun dumpHierarchy(tag: String) {
        val path = "/sdcard/Pictures/hierarchy_$tag.xml"
        shell("uiautomator dump $path")
    }

    private fun shell(cmd: String) {
        instrumentation.uiAutomation.executeShellCommand(cmd).use { }
    }
}
