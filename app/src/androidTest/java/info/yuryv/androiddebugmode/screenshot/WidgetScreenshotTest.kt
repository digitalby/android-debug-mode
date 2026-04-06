package info.yuryv.androiddebugmode.screenshot

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Point
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        val bounds = widgetTile.visibleBounds
        Log.d(TAG, "WIDGET_TILE: cls=${widgetTile.className} bounds=$bounds")

        // Use GestureDescription to send a long-press (1 500 ms hold, well above
        // Launcher3's 400 ms threshold) followed by a slow 2-second drag.  This
        // goes through the accessibility-service gesture path which guarantees the
        // hold fires before any movement, unlike UiObject2.drag() whose 500 ms
        // hold can race with RecyclerView's scroll-detection.
        val dropX = device.displayWidth / 2
        val dropY = device.displayHeight / 3   // ≈800 px on Pixel 6, middle of grid
        Log.d(TAG, "GESTURE: hold at (${bounds.centerX()},${bounds.centerY()}) then drag to ($dropX,$dropY)")

        dispatchLongPressDrag(
            startX = bounds.centerX(),
            startY = bounds.centerY(),
            endX = dropX,
            endY = dropY,
            holdMs = 1_500L,
            dragMs = 2_000L,
        )
        Thread.sleep(3_000)

        captureScreen("after_drag")
        logHierarchy("after_drag")
        logAllTexts("after_drag")
    }

    /**
     * Dispatches a long-press-then-drag gesture via the accessibility service.
     * Stroke 1: stay at (startX,startY) for [holdMs] ms (long-press fires well before
     *           the stroke ends, since the finger doesn't move past TouchSlop).
     * Stroke 2: slide from (startX,startY) to (endX,endY) over [dragMs] ms.
     * The two strokes are chained (willContinue=true) into a single touch sequence.
     */
    private fun dispatchLongPressDrag(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        holdMs: Long,
        dragMs: Long,
    ) {
        // A path with a sub-pixel move keeps the gesture provider from rejecting a
        // zero-length path while remaining well within Android's default TouchSlop.
        val holdPath = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(startX.toFloat() + 0.1f, startY.toFloat())
        }
        val holdStroke = GestureDescription.StrokeDescription(
            holdPath,
            /* startTime= */ 0L,
            holdMs,
            /* willContinue= */ true,
        )

        val dragPath = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val dragStroke = holdStroke.continueStroke(
            dragPath,
            /* delay= */ 0L,
            dragMs,
            /* willContinue= */ false,
        )

        val gesture = GestureDescription.Builder()
            .addStroke(holdStroke)
            .addStroke(dragStroke)
            .build()

        val latch = CountDownLatch(1)
        instrumentation.uiAutomation.dispatchGesture(
            gesture,
            object : GestureDescription.Callback() {
                override fun onCompleted(g: GestureDescription) {
                    Log.d(TAG, "GESTURE_COMPLETED")
                    latch.countDown()
                }
                override fun onCancelled(g: GestureDescription) {
                    Log.d(TAG, "GESTURE_CANCELLED")
                    latch.countDown()
                }
            },
            null,
        )
        val dispatched = latch.await(holdMs + dragMs + 5_000, TimeUnit.MILLISECONDS)
        Log.d(TAG, "GESTURE_AWAIT: dispatched=$dispatched")
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
