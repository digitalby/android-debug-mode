package info.yuryv.androiddebugmode

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [DebugModeWidgetReceiver].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DebugModeWidgetReceiverTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun receiver_can_be_instantiated() {
        // Sanity check: the receiver class is wired correctly and can be constructed.
        val receiver = DebugModeWidgetReceiver()
        assertNotNull(receiver.glanceAppWidget)
    }

    @Test
    fun onReceive_boot_completed_does_not_throw() {
        // When BOOT_COMPLETED arrives (with no placed widgets), onReceive should
        // complete without error. AppWidgetManager returns an empty ID array in the
        // Robolectric environment, so onUpdate is called with zero widget IDs.
        val receiver = DebugModeWidgetReceiver()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, intent)
    }

    @Test
    fun onReceive_appwidget_update_does_not_throw() {
        val receiver = DebugModeWidgetReceiver()
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
            component = ComponentName(context, DebugModeWidgetReceiver::class.java)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf())
        }
        receiver.onReceive(context, intent)
    }
}
