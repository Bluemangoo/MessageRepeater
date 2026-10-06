package net.bluemangoo.messagerepeater.data

// UI 展示用的应用信息（包含图标，不建议存入数据库，仅供内存和界面使用）
data class AppDisplayInfo(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable, // 系统返回的图标
    val ruleCount: Int = 0
)