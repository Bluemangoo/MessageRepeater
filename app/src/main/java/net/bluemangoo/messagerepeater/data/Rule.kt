package net.bluemangoo.messagerepeater.data

import android.app.Notification
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.bluemangoo.messagerepeater.data.LogicOp.*
import net.bluemangoo.messagerepeater.data.RuleType.*
import net.bluemangoo.messagerepeater.util.NotificationPersistenceType
import net.bluemangoo.messagerepeater.util.ongoing_type
import net.bluemangoo.messagerepeater.util.text
import net.bluemangoo.messagerepeater.util.title

data class RuleMessage(val notification: Notification) {
    val title: String? = notification.title
    val text: String? = notification.text
}

@Serializable
enum class RuleType {
    STARTS_WITH,
    ENDS_WITH,
    INCLUDES,
    EQUALS_TO,
    REGEX
}

sealed interface RuleValueType {
    data object STRING : RuleValueType
    data class ENUM(val values: List<RuleValueEnum>) : RuleValueType
}

interface RuleValueEnum {
    val displayName: String
    val name: String
}

@Serializable
enum class RuleOn(val valueType: RuleValueType) {
    TITLE(RuleValueType.STRING),
    TEXT(RuleValueType.STRING),
    PERSISTENCE_TYPE(RuleValueType.ENUM(NotificationPersistenceType.entries));
}

@Serializable
enum class LogicOp {
    AND, OR
}

@Serializable
sealed class RuleNode {
    abstract fun count(): Int
    abstract fun getValueWhen(message: RuleMessage): Boolean

    @Serializable
    @SerialName("condition")
    data class Condition(
        val ruleType: RuleType,
        val ruleOn: RuleOn,
        val keyword: String
    ) : RuleNode() {
        override fun count(): Int {
            return 1
        }

        override fun getValueWhen(message: RuleMessage): Boolean {
            val on = when (ruleOn) {
                RuleOn.TITLE -> message.title
                RuleOn.TEXT -> message.text
                RuleOn.PERSISTENCE_TYPE -> message.notification.ongoing_type.name
            }
            return on != null && when (ruleType) {
                STARTS_WITH -> on.startsWith(keyword)
                ENDS_WITH -> on.endsWith(keyword)
                INCLUDES -> on.contains(keyword)
                EQUALS_TO -> on == keyword
                REGEX -> try {
                    Regex(this.keyword).containsMatchIn(keyword)
                } catch (_: Exception) {
                    false
                }
            }

        }
    }

    @Serializable
    @SerialName("group")
    data class Group(
        val op: LogicOp,
        val children: List<RuleNode>
    ) : RuleNode() {
        override fun count(): Int {
            return children.size
        }

        override fun getValueWhen(message: RuleMessage): Boolean {
            when (op) {
                AND -> {
                    children.forEach {
                        if (!it.getValueWhen(message)) {
                            return false
                        }
                    }
                    return true
                }

                OR -> {
                    if (children.isEmpty()) {
                        return true
                    }
                    children.forEach {
                        if (it.getValueWhen(message)) {
                            return true
                        }
                    }
                    return false
                }
            }
        }
    }

    @Serializable
    @SerialName("not")
    data class Not(
        val child: RuleNode
    ) : RuleNode() {
        override fun count(): Int {
            return 1
        }

        override fun getValueWhen(message: RuleMessage): Boolean {
            return !child.getValueWhen(message)
        }
    }
}
