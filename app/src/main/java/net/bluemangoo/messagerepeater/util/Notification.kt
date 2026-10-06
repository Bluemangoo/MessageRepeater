package net.bluemangoo.messagerepeater.util

import android.app.Notification
import kotlinx.serialization.Serializable
import net.bluemangoo.messagerepeater.data.RuleValueEnum

val Notification.title: String?
    get() = extras.getString(Notification.EXTRA_TITLE)

val Notification.text: String?
    get() = extras.getString(Notification.EXTRA_TEXT)

@Serializable
enum class NotificationPersistenceType(override val displayName: String) : RuleValueEnum {
    DISMISSIBLE("可清除"),

    ONGOING("常驻");

    companion object {
        fun fromNotification(notification: Notification): NotificationPersistenceType {
            val permanentMask = Notification.FLAG_ONGOING_EVENT or
                    Notification.FLAG_NO_CLEAR or
                    Notification.FLAG_FOREGROUND_SERVICE or
                    0x00040000 // FLAG_PROMOTED_ONGOING

            return if ((notification.flags and permanentMask) != 0) {
                ONGOING
            } else {
                DISMISSIBLE
            }
        }
    }
}

val Notification.ongoing_type: NotificationPersistenceType
    get() = NotificationPersistenceType.fromNotification(this)