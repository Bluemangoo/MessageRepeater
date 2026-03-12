package net.bluemangoo.messagerepeater.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import net.bluemangoo.messagerepeater.AppUtils

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            AppUtils.requestRebindNotificationService(context)
        }
    }
}