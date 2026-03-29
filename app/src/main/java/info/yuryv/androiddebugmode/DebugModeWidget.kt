package info.yuryv.androiddebugmode

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentWidth
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

internal object WidgetTestTags {
    const val USB_ROW = "usb_row"
    const val WIFI_ROW = "wifi_row"
    const val USB_CHIP = "usb_chip"
    const val WIFI_CHIP = "wifi_chip"
    const val REFRESH_BTN = "btn_refresh"
}

class DebugModeWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val usbEnabled = readSetting(context, Settings.Global.ADB_ENABLED)
        val wifiEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            readSetting(context, "adb_wifi_enabled")
        } else {
            false
        }
        val showWifiRow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        provideContent {
            GlanceTheme {
                WidgetContent(
                    usbEnabled = usbEnabled,
                    wifiEnabled = wifiEnabled,
                    showWifiRow = showWifiRow,
                )
            }
        }
    }

    internal fun readSetting(context: Context, key: String): Boolean = try {
        Settings.Global.getInt(context.contentResolver, key, 0) != 0
    } catch (_: SecurityException) {
        false
    }
}

@Composable
internal fun WidgetContent(
    usbEnabled: Boolean,
    wifiEnabled: Boolean,
    showWifiRow: Boolean,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Debug Mode",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Refresh",
                colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurface),
                modifier = GlanceModifier
                    .size(20.dp)
                    .semantics { testTag = WidgetTestTags.REFRESH_BTN }
                    .clickable(actionRunCallback<RefreshWidgetAction>()),
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        DebugStatusRow(
            iconResId = R.drawable.ic_adb,
            iconContentDesc = "USB debug",
            label = "USB Debugging",
            isEnabled = usbEnabled,
            rowTestTag = WidgetTestTags.USB_ROW,
            chipTestTag = WidgetTestTags.USB_CHIP,
        )

        if (showWifiRow) {
            Spacer(modifier = GlanceModifier.height(4.dp))
            DebugStatusRow(
                iconResId = R.drawable.ic_wifi_tethering,
                iconContentDesc = "Wireless debug",
                label = "Wireless Debugging",
                isEnabled = wifiEnabled,
                rowTestTag = WidgetTestTags.WIFI_ROW,
                chipTestTag = WidgetTestTags.WIFI_CHIP,
            )
        }
    }
}

@Composable
private fun DebugStatusRow(
    iconResId: Int,
    iconContentDesc: String,
    label: String,
    isEnabled: Boolean,
    rowTestTag: String,
    chipTestTag: String,
) {
    val chipBackground = if (isEnabled) {
        ColorProvider(Color(0xFF388E3C))
    } else {
        ColorProvider(Color(0xFF616161))
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .semantics { testTag = rowTestTag }
            .clickable(actionRunCallback<OpenSettingsAction>())
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(iconResId),
            contentDescription = iconContentDesc,
            colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurface),
            modifier = GlanceModifier.size(20.dp),
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = label,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 12.sp,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Box(
            modifier = GlanceModifier
                .wrapContentWidth()
                .background(chipBackground)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isEnabled) "ON" else "OFF",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFFFFFFF)),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = GlanceModifier.semantics { testTag = chipTestTag },
            )
        }
    }
}
