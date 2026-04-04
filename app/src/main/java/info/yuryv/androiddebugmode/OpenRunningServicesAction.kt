package info.yuryv.androiddebugmode

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll

class OpenRunningServicesAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        DebugModeWidget().updateAll(context)
        context.startActivity(
            Intent("android.settings.APPLICATION_RUNNING_SERVICES")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
