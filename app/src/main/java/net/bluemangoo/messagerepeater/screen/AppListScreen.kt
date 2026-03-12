package net.bluemangoo.messagerepeater.screen

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.bluemangoo.messagerepeater.AppUtils
import net.bluemangoo.messagerepeater.R
import net.bluemangoo.messagerepeater.data.AppDatabase
import net.bluemangoo.messagerepeater.data.AppDisplayInfo
import net.bluemangoo.messagerepeater.data.RuleDao

object AppList {
    var inner: List<AppDisplayInfo> = emptyList()
    suspend fun update(context: Context, db: RuleDao): List<AppDisplayInfo> {
        inner = AppUtils.getAppDisplayInfos(context, db)
        return inner
    }

    suspend fun drop(apps: Collection<String>, context: Context, db: RuleDao): List<AppDisplayInfo> {
        apps.forEach {
            db.deleteRuleByPackage(it)
        }
        return this.update(context, db)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppListScreen(context: Context, onAppClicked: (packageName: String) -> Unit, onAddClicked: () -> Unit) {
    val scope = rememberCoroutineScope()


    var appList by remember { mutableStateOf(AppList.inner) }

    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    val inSelectionMode = selectedIds.isNotEmpty()

    val db = remember { AppDatabase.getDatabase(context).ruleDao() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            val list = AppList.update(context, db)
            if (!isActive) {
                return@launch
            }
            appList = list
        }
    }

    Scaffold(
        topBar = {
            if (inSelectionMode) {
                TopAppBar(
                    title = { Text("已选择 ${selectedIds.size} 项") },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(painter = painterResource(R.drawable.close_24px), contentDescription = "取消")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            appList = appList.filterNot { it.packageName in selectedIds }
                            val selectedIdsCopy = selectedIds
                            selectedIds = emptySet()
                            scope.launch {
                                appList = AppList.drop(selectedIdsCopy, context, db)
                            }
                        }) {
                            Icon(painter = painterResource(R.drawable.delete_24px), contentDescription = "删除")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text("通知复读机") },
                    colors = TopAppBarDefaults.topAppBarColors()
                )
            }
        },
        floatingActionButton = {
            if (!inSelectionMode) {
                FloatingActionButton(onClick = onAddClicked) {
                    Icon(painter = painterResource(R.drawable.add_24px), contentDescription = "添加应用")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(appList, key = { it.packageName }) { app ->
                val isSelected = selectedIds.contains(app.packageName)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (inSelectionMode) {
                                    selectedIds =
                                        if (isSelected) selectedIds - app.packageName else selectedIds + app.packageName
                                } else {
                                    onAppClicked(app.packageName)
                                }
                            },
                            onLongClick = {
                                if (!inSelectionMode) {
                                    selectedIds = selectedIds + app.packageName
                                }
                            }
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = app.icon,
                            contentDescription = "${app.appName} 图标",
                            modifier = Modifier.size(48.dp)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "${app.ruleCount} 条规则",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}