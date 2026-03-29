package info.yuryv.androiddebugmode

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [DebugModeWidget.readSetting].
 *
 * Uses Robolectric to provide a real Android [android.content.ContentResolver]
 * backed by Robolectric's in-process Settings provider — no device required.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReadSettingTest {

    private lateinit var context: Context
    private lateinit var widget: DebugModeWidget

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        widget = DebugModeWidget()
    }

    @Test
    fun returns_true_when_setting_is_1() {
        Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 1)
        assertTrue(widget.readSetting(context, Settings.Global.ADB_ENABLED))
    }

    @Test
    fun returns_false_when_setting_is_0() {
        Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0)
        assertFalse(widget.readSetting(context, Settings.Global.ADB_ENABLED))
    }

    @Test
    fun returns_false_for_unknown_key() {
        // A key that has never been written defaults to 0, so readSetting returns false.
        assertFalse(widget.readSetting(context, "key_that_does_not_exist"))
    }
}
