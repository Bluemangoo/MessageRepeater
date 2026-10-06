package net.bluemangoo.messagerepeater

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import kotlinx.serialization.json.Json
import net.bluemangoo.messagerepeater.data.AppDisplayInfo
import net.bluemangoo.messagerepeater.data.RuleDao
import net.bluemangoo.messagerepeater.data.RuleNode
import net.bluemangoo.messagerepeater.service.NotificationForwarderService

object AppUtils {

    fun getInstalledApps(context: Context): List<AppDisplayInfo> {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        val appList = mutableListOf<AppDisplayInfo>()

        for (appInfo in packages) {
            if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                val appName = pm.getApplicationLabel(appInfo).toString()
                val packageName = appInfo.packageName
                val icon = pm.getApplicationIcon(appInfo)

                appList.add(
                    AppDisplayInfo(
                        packageName = packageName,
                        appName = appName,
                        icon = icon,
                        ruleCount = 0
                    )
                )
            }
        }

        return appList.sortedBy { it.appName }
    }

    suspend fun getAppDisplayInfos(context: Context, db: RuleDao): List<AppDisplayInfo> {
        val pm = context.packageManager
        val allRulesFlow = db.getAllRulesFlow()
        val appList = mutableListOf<AppDisplayInfo>()

        for ((packageName, ruleTreeJson) in allRulesFlow) {
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val rule = Json.decodeFromString<RuleNode>(ruleTreeJson)
                appList.add(
                    AppDisplayInfo(
                        packageName = packageName,
                        appName = pm.getApplicationLabel(appInfo).toString(),
                        icon = pm.getApplicationIcon(appInfo),
                        ruleCount = rule.count()
                    )
                )
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }
        return appList
    }

    fun getAppInfo(packageName: String, context: Context): AppDisplayInfo {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(packageName, 0)
        val appName = pm.getApplicationLabel(appInfo).toString()
        val packageName = appInfo.packageName
        val icon = pm.getApplicationIcon(appInfo)
        return AppDisplayInfo(
            packageName = packageName,
            appName = appName,
            icon = icon,
            ruleCount = 0
        )
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val packageNames = NotificationManagerCompat.getEnabledListenerPackages(context)
        return packageNames.contains(context.packageName)
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun requestRebindNotificationService(context: Context) {
        if (isNotificationListenerEnabled(context)) {
            val componentName = ComponentName(context, NotificationForwarderService::class.java)
            android.service.notification.NotificationListenerService.requestRebind(componentName)
        }
    }
}