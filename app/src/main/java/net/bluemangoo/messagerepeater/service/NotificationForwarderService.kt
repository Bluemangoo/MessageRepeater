package net.bluemangoo.messagerepeater.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import net.bluemangoo.messagerepeater.AppUtils
import net.bluemangoo.messagerepeater.MainActivity
import net.bluemangoo.messagerepeater.data.AppDatabase
import net.bluemangoo.messagerepeater.data.AppDisplayInfo
import net.bluemangoo.messagerepeater.data.RuleMessage
import net.bluemangoo.messagerepeater.data.RuleNode
import net.bluemangoo.messagerepeater.util.text
import net.bluemangoo.messagerepeater.util.title
import kotlin.time.Duration.Companion.seconds

class NotificationForwarderService : NotificationListenerService() {
    private val db = AppDatabase.getDatabase(this).ruleDao()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val CHANNEL_ID = "forwarder_channel"
        private const val TAG = "ForwarderService"
        private const val ALIVE_CHANNEL_ID = "keep_alive_channel"
        private const val ALIVE_NOTIFICATION_ID = 999

        private val _isRealConnected = MutableStateFlow(false)
        val isRealConnected = _isRealConnected.asStateFlow()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isRealConnected.value = true
        Log.d(TAG, "活体检测：服务已真正连接到系统通知总线！")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isRealConnected.value = false
        Log.e(TAG, "活体检测：服务被系统断开连接！")
    }

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
            startHeartbeat()
            Log.d(TAG, "通知复读机已成功转为前台服务并开启守护！")
        } catch (e: Exception) {
            Log.e(TAG, "转为前台服务失败: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun startHeartbeat() {
        serviceScope.launch {
            while (isActive) {
                delay(60.seconds)
                val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(ALIVE_NOTIFICATION_ID, buildAliveNotification())
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        val notification = sbn.notification

        val rule = runBlocking {
            val config = db.getRuleByPackage(packageName) ?: return@runBlocking null
            Json.decodeFromString<RuleNode>(config.ruleTreeJson)
        }
        if (rule == null) {
            return
        }

        val originalIntent = notification.contentIntent
        val notificationId = sbn.key.hashCode()

        if (packageName == applicationContext.packageName) return

        if (rule.getValueWhen(RuleMessage(sbn.notification))) {
            sendRepeaterNotification(
                notificationId,
                notification.title,
                notification.text,
                AppUtils.getAppInfo(packageName, this),
                originalIntent
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return

        if (sbn.packageName == applicationContext.packageName) return

        val notificationId = sbn.key.hashCode()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    private fun sendRepeaterNotification(
        notificationId: Int,
        originalTitle: String?,
        originalText: String?,
        appInfo: AppDisplayInfo,
        contentIntent: PendingIntent?
    ) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // 使用系统自带的提示图标
            .setContentTitle("${appInfo.appName}: $originalTitle")
            .setContentText(originalText)
            .setAutoCancel(true)

        if (contentIntent != null) {
            notification.setContentIntent(contentIntent)
        }

        notificationManager.notify(notificationId, notification.build())
    }

    private fun createNotificationChannel() {
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

    private fun buildAliveNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, ALIVE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("复读机已启动")
            .setContentText("通知复读机服务已启动！")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

}