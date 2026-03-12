package net.bluemangoo.messagerepeater.data

// 单条匹配规则
data class MatchRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: RuleType,
    val keyword: String
)

// UI 展示用的应用信息（包含图标，不建议存入数据库，仅供内存和界面使用）
data class AppDisplayInfo(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable, // 系统返回的图标
    val ruleCount: Int = 0
)

// 准备存入数据库的核心配置（不需要存图标，需要时通过包名去系统拿）
data class AppConfig(
    val packageName: String,
    val appName: String,
    val rules: List<MatchRule> = emptyList()
)