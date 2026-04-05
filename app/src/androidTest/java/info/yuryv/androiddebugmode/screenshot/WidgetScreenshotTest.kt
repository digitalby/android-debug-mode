package info.yuryv.androiddebugmode.screenshot

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
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
        // The widget picker is a RecyclerView — scroll until the "Debug Mode" app section
        // is visible, then drag the widget tile to the home screen.
        @Suppress("DEPRECATION")
        val scroller = UiScrollable(
            UiSelector().scrollable(true).packageName("com.android.launcher3")
        )
        @Suppress("DEPRECATION")
        val found = try {
            scroller.scrollIntoView(UiSelector().textContains("Debug Mode"))
        } catch (_: Exception) {
            false
        }
        dumpHierarchy("after_scroll")

        if (!found) {
            // Fallback: scroll down manually a few times and wait
            val scrollable = device.findObject(
                By.scrollable(true).pkg("com.android.launcher3")
            )
            repeat(3) {
                scrollable?.scroll(Direction.DOWN, 0.5f)
                device.waitForIdle(500)
            }
            dumpHierarchy("after_manual_scroll")
        }

        val appSection = device.wait(
            Until.findObject(By.textContains("Debug Mode")),
            3_000,
        ) ?: error("App section 'Debug Mode' not found in widget picker after scrolling")

        // Tap the app header to expand the widget tile (if collapsed)
        appSection.click()
        device.waitForIdle(1_500)
        dumpHierarchy("after_app_header_click")

        // The draggable widget tile: try several selectors in order of specificity
        val widgetTile =
            device.wait(Until.findObject(By.descContains("Debug Mode Widget")), 2_000)
                ?: device.findObject(By.textContains("Debug Mode Widget"))
                ?: device.findObject(
                    By.textContains("Debug Mode").pkg("com.android.launcher3")
                )
                ?: error("Widget tile not found after expanding app section")

        dumpHierarchy("found_widget_tile")

        val bounds = widgetTile.visibleBounds
        val dropX = device.displayWidth / 2
        val dropY = device.displayHeight / 3
        device.drag(bounds.centerX(), bounds.centerY(), dropX, dropY, 60)
        device.waitForIdle(2_000)
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
     * Simulates a long press at the centre-upper area of the home screen via
     * `input touchscreen swipe`. A zero-distance swipe held for 2 s reliably
     * triggers the long-press gesture on Launcher3 without needing a specific
     * UiObject2 to call longClick() on.
     */
    private fun longPressHomeScreen() {
        val x = device.displayWidth / 2
        val y = device.displayHeight / 3
        shell("input touchscreen swipe $x $y $x $y 2000")
        device.waitForIdle(3_000)
    }

    /** Dumps the current window hierarchy for CI artifact analysis. */
    private fun dumpHierarchy(tag: String) {
        runCatching {
            device.dumpWindowHierarchy(File("/sdcard/Pictures/hierarchy_$tag.xml"))
        }
    }

    private fun shell(cmd: String) {
        instrumentation.uiAutomation.executeShellCommand(cmd).use { }
    }
}
