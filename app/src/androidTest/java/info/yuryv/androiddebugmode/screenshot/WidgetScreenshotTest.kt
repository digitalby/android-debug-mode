package info.yuryv.androiddebugmode.screenshot

import android.util.Log
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
import java.io.ByteArrayOutputStream
import java.io.File

private const val LAUNCHER_PKG = "com.android.launcher3"
private const val TAG = "WDIAG"

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
        captureScreen("after_long_press")

        val widgetsBtn = device.wait(Until.findObject(By.text("Widgets")), 5_000)
            ?: error("'Widgets' button not found after long-pressing the home screen")
        widgetsBtn.click()

        // Wait for the Launcher3 widget picker to be in the foreground —
        // not just for idle, because a heads-up notification may appear
        // and delay the picker transition.
        val pickerVisible = device.wait(
            Until.hasObject(By.pkg(LAUNCHER_PKG).scrollable(true)),
            8_000,
        )

        // If a notification shade or other overlay opened on top, close it.
        if (!pickerVisible) {
            device.pressBack()
            device.waitForIdle(1_000)
        }

        captureScreen("after_widgets_click")
        logHierarchy("after_widgets_click")
        logAllTexts("after_widgets_click")
    }

    private fun placeWidget() {
        // Use UiScrollable so the scroll stays inside the Launcher3 RecyclerView
        // and cannot accidentally dismiss the picker or open the app drawer.
        val picker = UiScrollable(
            UiSelector()
                .packageName(LAUNCHER_PKG)
                .scrollable(true),
        )
        picker.setMaxSearchSwipes(25)

        val found = try {
            picker.scrollIntoView(UiSelector().textContains("Debug Mode"))
        } catch (_: Exception) {
            false
        }

        val target = device.findObject(By.textContains("Debug Mode").pkg(LAUNCHER_PKG))
        if (target == null) {
            captureScreen("not_found")
            logHierarchy("not_found")
            logAllTexts("not_found")
            error("App section 'Debug Mode' not found in widget picker (scrollIntoView=$found)")
        }

        captureScreen("found_app_section")

        // Tap the app header to expand the widget tile if collapsed.
        target.click()
        device.waitForIdle(1_500)
        captureScreen("after_app_header_click")
        logAllTexts("after_app_header_click")

        // Draggable widget tile: try selectors in order of specificity.
        val widgetTile =
            device.wait(Until.findObject(By.descContains("Debug Mode Widget")), 2_000)
                ?: device.findObject(By.textContains("Debug Mode Widget"))
                ?: device.findObject(By.textContains("Debug Mode").pkg(LAUNCHER_PKG))
                ?: error("Widget tile not found after expanding app section")

        captureScreen("found_widget_tile")

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
    // Diagnostics
    // ---------------------------------------------------------------------------

    private fun longPressHomeScreen() {
        val x = device.displayWidth / 2
        val y = device.displayHeight / 3
        shell("input touchscreen swipe $x $y $x $y 2000")
        device.waitForIdle(3_000)
    }

    /** Saves a half-resolution screenshot to /sdcard/Pictures/ for CI artifact upload. */
    private fun captureScreen(tag: String) {
        runCatching {
            val dir = File("/sdcard/Pictures").also { it.mkdirs() }
            device.takeScreenshot(File(dir, "screen_$tag.png"), 0.5f, 80)
        }
    }

    /** Logs the full UI hierarchy via logcat (tag WDIAG). */
    private fun logHierarchy(tag: String) {
        runCatching {
            val bos = ByteArrayOutputStream()
            device.dumpWindowHierarchy(bos)
            val xml = bos.toString("UTF-8")
            xml.chunked(4000).forEachIndexed { i, chunk ->
                Log.d(TAG, "HIERARCHY[$tag][$i]: $chunk")
            }
        }
    }

    /** Logs every accessible text/desc element via logcat (tag WDIAG). */
    private fun logAllTexts(tag: String) {
        runCatching {
            val lines = device.findObjects(By.enabled(true)).mapNotNull { el ->
                val t = el.text?.takeIf { it.isNotBlank() }
                val d = el.contentDescription?.takeIf { it.isNotBlank() }
                if (t != null || d != null) "t='$t' d='$d' cls=${el.className} pkg=${el.applicationPackage}"
                else null
            }
            Log.d(TAG, "TEXTS[$tag]: ${lines.joinToString(" || ")}")
        }
    }

    private fun shell(cmd: String) {
        instrumentation.uiAutomation.executeShellCommand(cmd).use { }
    }
}
