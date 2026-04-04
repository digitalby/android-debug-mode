package info.yuryv.androiddebugmode

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the widget's Glance composable content.
 *
 * Uses [runGlanceAppWidgetUnitTest] which runs as a pure JVM unit test —
 * no device or emulator required.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WidgetContentTest {

    @Test
    fun usb_row_always_present() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetContent(usbEnabled = false, wifiEnabled = false, showWifiRow = false)
        }
        onNode(hasTestTag(WidgetTestTags.USB_ROW)).assertExists()
    }

    @Test
    fun usb_chip_shows_ON_when_enabled() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetContent(usbEnabled = true, wifiEnabled = false, showWifiRow = false)
        }
        onNode(hasTestTag(WidgetTestTags.USB_CHIP)).assert(hasText("ON"))
    }

    @Test
    fun usb_chip_shows_OFF_when_disabled() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetContent(usbEnabled = false, wifiEnabled = false, showWifiRow = false)
        }
        onNode(hasTestTag(WidgetTestTags.USB_CHIP)).assert(hasText("OFF"))
    }

    @Test
    fun wifi_row_present_when_showWifiRow_true() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetContent(usbEnabled = false, wifiEnabled = false, showWifiRow = true)
        }
        onNode(hasTestTag(WidgetTestTags.WIFI_ROW)).assertExists()
    }

    @Test
    fun wifi_row_absent_when_showWifiRow_false() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetContent(usbEnabled = false, wifiEnabled = false, showWifiRow = false)
        }
        onNode(hasTestTag(WidgetTestTags.WIFI_ROW)).assertDoesNotExist()
    }

    @Test
    fun wifi_chip_shows_ON_when_enabled() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetContent(usbEnabled = false, wifiEnabled = true, showWifiRow = true)
        }
        onNode(hasTestTag(WidgetTestTags.WIFI_CHIP)).assert(hasText("ON"))
    }

    @Test
    fun wifi_chip_shows_OFF_when_disabled() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetContent(usbEnabled = false, wifiEnabled = false, showWifiRow = true)
        }
        onNode(hasTestTag(WidgetTestTags.WIFI_CHIP)).assert(hasText("OFF"))
    }

    @Test
    fun refresh_button_present() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetContent(usbEnabled = false, wifiEnabled = false, showWifiRow = false)
        }
        onNode(hasTestTag(WidgetTestTags.REFRESH_BTN)).assertExists()
    }

    @Test
    fun running_services_row_always_present() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetContent(usbEnabled = false, wifiEnabled = false, showWifiRow = false)
        }
        onNode(hasTestTag(WidgetTestTags.RUNNING_SERVICES_ROW)).assertExists()
    }

    @Test
    fun running_services_row_present_when_wifi_row_shown() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetContent(usbEnabled = true, wifiEnabled = true, showWifiRow = true)
        }
        onNode(hasTestTag(WidgetTestTags.RUNNING_SERVICES_ROW)).assertExists()
    }
}
