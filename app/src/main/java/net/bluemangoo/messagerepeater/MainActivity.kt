package net.bluemangoo.messagerepeater

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.bluemangoo.messagerepeater.data.AppDatabase
import net.bluemangoo.messagerepeater.data.AppRuleConfig
import net.bluemangoo.messagerepeater.data.LogicOp
import net.bluemangoo.messagerepeater.data.RuleNode
import net.bluemangoo.messagerepeater.screen.AppListScreen
import net.bluemangoo.messagerepeater.screen.AppSelectionScreen
import net.bluemangoo.messagerepeater.screen.RuleEditorScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppUtils.requestRebindNotificationService(this)
        setContent {
            MaterialTheme {
                val db = remember { AppDatabase.getDatabase(this).ruleDao() }

                PermissionCheckScreen(context = this)

                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        AppListScreen(
                            context = this@MainActivity,
                            onAppClicked = { packageName ->
                                navController.navigate("editor/$packageName")
                            },
                            onAddClicked = {
                                navController.navigate("selectApp")
                            }
                        )
                    }

                    composable("selectApp") {
                        AppSelectionScreen(
                            context = this@MainActivity,
                            onNavigateBack = { navController.popBackStack() },
                            onAppSelected = { selectedApp ->
                                runBlocking {
                                    coroutineScope {
                                        val emptyRuleJson =
                                            Json.encodeToString<RuleNode>(RuleNode.Group(LogicOp.AND, emptyList()))
                                        db.saveRule(
                                            AppRuleConfig(
                                                selectedApp.packageName,
                                                emptyRuleJson
                                            )
                                        )

                                        navController.popBackStack()
                                    }
                                }
                            }
                        )
                    }

                    composable("editor/{packageName}") { backStackEntry ->
                        val pkg = backStackEntry.arguments?.getString("packageName") ?: ""

                        RuleEditorScreen(
                            packageName = pkg,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionCheckScreen(context: Activity) {
    var hasPermission by remember { mutableStateOf(AppUtils.isNotificationListenerEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = AppUtils.isNotificationListenerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        requestPermissions(context, arrayOf("android.permission.POST_NOTIFICATIONS"), 100)
    }

    if (!hasPermission) {
        AlertDialog(
            onDismissRequest = { /* 强制要求权限，不允许点击外部关闭 */ },
            title = { Text("需要通知读取权限") },
            text = { Text("为了能够复读通知，请在接下来的系统设置页面中，允许本应用读取通知。") },
            confirmButton = {
                Button(onClick = {
                    AppUtils.openNotificationSettings(context)
                }) {
                    Text("去授权")
                }
            }
        )
    }
}