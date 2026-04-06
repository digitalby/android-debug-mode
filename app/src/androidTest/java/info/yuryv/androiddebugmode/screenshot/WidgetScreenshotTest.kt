package info.yuryv.androiddebugmode.screenshot

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WidgetScreenshotTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    // /data/local/tmp is always writable by the shell UID (unlike /sdcard which
    // requires external storage permissions from the test process's file API).
    private val diagDir = File("/data/local/tmp/screenshot_diag").also { it.mkdirs() }

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
        dumpAllTexts("after_widgets_click")
    }

    private fun placeWidget() {
        // Scroll every visible scrollable container, checking for "Debug Mode" after
        // each scroll step. This avoids depending on UiScrollable's internal behaviour
        // and works regardless of how many scrollable views the picker has.
        val target = scrollUntilVisible("Debug Mode", maxSwipes = 15)
            ?: run {
                dumpHierarchy("not_found")
                dumpAllTexts("not_found")
                error("App section 'Debug Mode' not found in widget picker after scrolling")
            }

        dumpHierarchy("found_app_section")

        // Tap the app header to expand the widget tile if the picker groups by app.
        target.click()
        device.waitForIdle(1_500)
        dumpHierarchy("after_app_header_click")
        dumpAllTexts("after_app_header_click")

        // The draggable widget tile: try several selectors in order of specificity.
        val widgetTile =
            device.wait(Until.findObject(By.descContains("Debug Mode Widget")), 2_000)
                ?: device.findObject(By.textContains("Debug Mode Widget"))
                ?: device.findObject(By.textContains("Debug Mode"))
                ?: error("Widget tile not found after expanding app section")

        dumpHierarchy("found_widget_tile")

        val bounds = widgetTile.visibleBounds
        val dropX = device.displayWidth / 2
        val dropY = device.displayHeight / 3
        device.drag(bounds.centerX(), bounds.centerY(), dropX, dropY, 60)
        device.waitForIdle(2_000)
    }

    private fun waitForGlanceRender() {
        Thread.sleep(3_000)
    }

    private fun takeScreenshot() {
        val out = File("/sdcard/Pictures/01_widget.png")
        out.parentFile?.mkdirs()
        device.takeScreenshot(out, 1.0f, 100)
    }

    // ---------------------------------------------------------------------------
    // Scrolling
    // ---------------------------------------------------------------------------

    /**
     * Scrolls all visible scrollable containers downward until an element matching
     * [textSubstring] is found, returning it. Returns null if not found after
     * [maxSwipes] swipe attempts across all containers.
     */
    private fun scrollUntilVisible(textSubstring: String, maxSwipes: Int): androidx.test.uiautomator.UiObject2? {
        repeat(maxSwipes) {
            val found = device.findObject(By.textContains(textSubstring))
            if (found != null) return found

            // Scroll every scrollable container one notch downward.
            val scrollables = device.findObjects(By.scrollable(true))
            if (scrollables.isEmpty()) device.swipe(
                device.displayWidth / 2, device.displayHeight * 2 / 3,
                device.displayWidth / 2, device.displayHeight / 3,
                20,
            )
            scrollables.forEach { it.scroll(Direction.DOWN, 0.4f) }
            device.waitForIdle(400)
        }
        return device.findObject(By.textContains(textSubstring))
    }

    // ---------------------------------------------------------------------------
    // Diagnostics
    // ---------------------------------------------------------------------------

    private fun longPressHomeScreen() {
        val x = device.displayWidth / 2
        val y = device.displayHeight / 3
        shell("input touchscreen swipe $x $y $x $y 2000")
        device.waitForIdle(3_000)
    }

    private fun dumpHierarchy(tag: String) {
        runCatching { device.dumpWindowHierarchy(File(diagDir, "hierarchy_$tag.xml")) }
    }

    private fun dumpAllTexts(tag: String) {
        runCatching {
            val lines = device.findObjects(By.enabled(true)).mapNotNull { el ->
                val t = el.text?.takeIf { it.isNotBlank() }
                val d = el.contentDescription?.takeIf { it.isNotBlank() }
                if (t != null || d != null) "text='$t' desc='$d' cls='${el.className}' pkg='${el.applicationPackage}'"
                else null
            }
            File(diagDir, "texts_$tag.txt").writeText(lines.joinToString("\n"))
        }
    }

    private fun shell(cmd: String) {
        instrumentation.uiAutomation.executeShellCommand(cmd).use { }
    }
}
