package net.bluemangoo.messagerepeater.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.bluemangoo.messagerepeater.AppUtils
import net.bluemangoo.messagerepeater.data.AppDatabase
import net.bluemangoo.messagerepeater.data.AppDisplayInfo
import net.bluemangoo.messagerepeater.data.RuleMessage
import net.bluemangoo.messagerepeater.data.RuleNode

class NotificationForwarderService : NotificationListenerService() {
    private val CHANNEL_ID = "forwarder_channel"
    private val TAG = "ForwarderService"
    private val ALIVE_CHANNEL_ID = "keep_alive_channel"
    private val ALIVE_NOTIFICATION_ID = 999
    private val db = AppDatabase.getDatabase(this).ruleDao()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "通知复读机服务已启动！")

        try {
            ServiceCompat.startForeground(
                this,
                ALIVE_NOTIFICATION_ID,
                buildAliveNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
            Log.d(TAG, "通知复读机已成功转为前台服务并开启守护！")
        } catch (e: Exception) {
            Log.e(TAG, "转为前台服务失败: ${e.message}")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName

        val rule = runBlocking {
            val config = db.getRuleByPackage(packageName) ?: return@runBlocking null
            Json.decodeFromString<RuleNode>(config.ruleTreeJson)
        }
        if (rule == null) {
            return
        }

        val extras = sbn.notification.extras
        val title = extras.getString("android.title")
        val text = extras.getCharSequence("android.text")?.toString()

        if (packageName == applicationContext.packageName) return

        if (rule.getValueWhen(RuleMessage(title, text))) {
            sendRepeaterNotification(title, text, AppUtils.getAppInfo(packageName, this))
        }
    }

    private fun sendRepeaterNotification(originalTitle: String?, originalText: String?, appInfo: AppDisplayInfo) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // 使用系统自带的提示图标
            .setContentTitle("${appInfo.appName}: $originalTitle")
            .setContentText(originalText)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val repeaterChannel = NotificationChannel(
                CHANNEL_ID,
                "复读机通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "用于展示匹配规则后转发的通知" }

            val keepAliveChannel = NotificationChannel(
                ALIVE_CHANNEL_ID,
                "后台保活服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "确保通知复读机在后台稳定运行" }

            manager.createNotificationChannel(repeaterChannel)
            manager.createNotificationChannel(keepAliveChannel)

        }
    }

    private fun buildAliveNotification(): Notification {
        return NotificationCompat.Builder(this, ALIVE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("复读机已启动")
            .setContentText("通知复读机服务已启动！")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

}