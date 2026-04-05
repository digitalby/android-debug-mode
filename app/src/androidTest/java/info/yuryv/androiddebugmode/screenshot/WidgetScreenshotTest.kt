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

    private fun pressHomeAndWait() {
        device.pressHome()
        device.waitForIdle(3_000)
    }

    private fun openWidgetPicker() {
        // Long-press the Launcher3 workspace to open the home screen edit panel
        val workspace = device.wait(
            Until.findObject(By.res("com.android.launcher3:id/workspace")),
            3_000,
        ) ?: device.wait(
            Until.findObject(By.pkg("com.android.launcher3").clazz("android.view.ViewGroup")),
            3_000,
        ) ?: error("Could not find launcher home screen to long-press")
        workspace.longClick()
        device.wait(Until.hasObject(By.text("Widgets")), 4_000)
        device.findObject(By.text("Widgets")).click()
        device.waitForIdle(2_000)
    }

    private fun placeWidget() {
        // Launcher3 groups the picker by app — find and expand our app's section
        val appHeader = device.wait(
            Until.findObject(By.textContains("Debug Mode")),
            5_000,
        ) ?: error("Widget app section not found in Launcher3 widget picker")
        appHeader.click()
        device.waitForIdle(1_000)

        // Prefer content-description match; fall back to text match
        val widgetPreview = device.wait(
            Until.findObject(By.descContains("Debug Mode").pkg("com.android.launcher3")),
            3_000,
        ) ?: device.findObject(By.textContains("Debug Mode Widget"))
            ?: error("Widget preview not found in picker")

        val bounds = widgetPreview.visibleBounds
        val dropX = device.displayWidth / 2
        val dropY = device.displayHeight / 4
        device.drag(bounds.centerX(), bounds.centerY(), dropX, dropY, 60)
        device.waitForIdle(2_000)
    }

    private fun waitForGlanceRender() {
        // Glance pushes RemoteViews asynchronously; wait for initial composition
        Thread.sleep(3_000)
    }

    private fun takeScreenshot() {
        val out = File("/sdcard/Pictures/01_widget.png")
        out.parentFile?.mkdirs()
        device.takeScreenshot(out, 1.0f, 100)
    }
}
