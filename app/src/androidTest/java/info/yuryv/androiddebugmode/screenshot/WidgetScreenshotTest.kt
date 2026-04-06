package info.yuryv.androiddebugmode.screenshot

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
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

        // The popup menu has a clickable FrameLayout container for all 3 items. Using
        // By.clickable(true).hasDescendant(By.text("Widgets")) returns the outermost
        // clickable ancestor (the container), not the specific row. Sending a raw
        // "input tap" via shell at the TEXT element's centre propagates naturally
        // through the view hierarchy to the correct OptionItem click handler.
        val widgetsText = device.wait(Until.findObject(By.text("Widgets").pkg(LAUNCHER_PKG)), 5_000)
            ?: error("'Widgets' text not found in popup after long-pressing the home screen")

        val cx = widgetsText.visibleBounds.centerX()
        val cy = widgetsText.visibleBounds.centerY()
        Log.d(TAG, "CLICK_TARGET: cls=${widgetsText.className} pkg=${widgetsText.applicationPackage} bounds=${widgetsText.visibleBounds}")
        captureScreen("before_click")

        // Retry the tap up to 3 times. The popup remains open between attempts so
        // retrying is safe. Each attempt collapses the notification shade immediately
        // after the tap so it cannot cover the widget picker.
        val pickerVisible = (1..3).any { attempt ->
            shell("input tap $cx $cy")
            captureScreen("after_tap_attempt_$attempt")
            Thread.sleep(1_000)
            shell("cmd statusbar collapse")
            Thread.sleep(300)
            val appeared = device.wait(Until.hasObject(By.pkg(LAUNCHER_PKG).scrollable(true)), 3_000) == true
            Log.d(TAG, "TAP_ATTEMPT[$attempt]: pickerAppeared=$appeared")
            appeared
        }
        if (!pickerVisible) {
            captureScreen("picker_not_opened")
            logHierarchy("picker_not_opened")
            logAllTexts("picker_not_opened")
            error("Widget picker did not open after 3 tap attempts on 'Widgets' popup item")
        }
        device.waitForIdle(1_000)

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

        // Draggable widget tile. Launcher3 uses desc="Debug Mode widget" (lowercase w)
        // on the title TextView inside the WidgetCell. Navigate up to the WidgetCell
        // parent so the drag starts on the correct container and triggers Launcher3's
        // drag-and-drop mode rather than being ignored by the child TextView.
        val widgetText =
            device.wait(Until.findObject(By.desc("Debug Mode widget").pkg(LAUNCHER_PKG)), 3_000)
                ?: device.findObject(By.desc("Debug Mode widget"))
                ?: error("Widget tile not found (desc='Debug Mode widget') after expanding app section")

        // parent is the WidgetCell container — the actual draggable view.
        val widgetTile = widgetText.parent ?: widgetText

        captureScreen("found_widget_tile")
        Log.d(TAG, "WIDGET_TILE: cls=${widgetTile.className} bounds=${widgetTile.visibleBounds}")

        val bounds = widgetTile.visibleBounds
        val dropX = device.displayWidth / 2
        // Drop near the top of the screen so the gesture clears the picker area.
        // The picker occupies most of the screen; the drag must travel far enough
        // upward for Launcher3 to detect placement on the home screen beneath.
        val dropY = device.displayHeight / 8
        device.drag(bounds.centerX(), bounds.centerY(), dropX, dropY, 120)
        device.waitForIdle(3_000)
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
