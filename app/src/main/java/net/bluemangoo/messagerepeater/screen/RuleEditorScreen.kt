package net.bluemangoo.messagerepeater.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.bluemangoo.messagerepeater.R
import net.bluemangoo.messagerepeater.data.AppDatabase
import net.bluemangoo.messagerepeater.data.AppRuleConfig
import net.bluemangoo.messagerepeater.data.LogicOp
import net.bluemangoo.messagerepeater.data.RuleNode
import net.bluemangoo.messagerepeater.data.RuleOn
import net.bluemangoo.messagerepeater.data.RuleType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorScreen(
    packageName: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context).ruleDao() }

    var rootRule by remember {
        mutableStateOf<RuleNode?>(null)
    }

    LaunchedEffect(packageName) {
        val savedConfig = db.getRuleByPackage(packageName)
        rootRule = if (savedConfig != null) {
            try {
                Json.decodeFromString<RuleNode>(savedConfig.ruleTreeJson)
            } catch (_: Exception) {
                RuleNode.Group(LogicOp.OR, emptyList())
            }
        } else {
            RuleNode.Group(LogicOp.OR, emptyList())
        }
    }

    BackHandler(enabled = true) {
        coroutineScope.launch {
            rootRule?.let { currentRule ->
                val json = Json.encodeToString(currentRule)
                db.saveRule(AppRuleConfig(packageName, json))
            }
            onNavigateBack()
        }
    }

    if (rootRule == null) {
        CircularProgressIndicator()
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("编辑通知匹配规则") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    scrolledContainerColor = Color.Unspecified,
                    navigationIconContentColor = Color.Unspecified,
                    titleContentColor = Color.Unspecified,
                    actionIconContentColor = Color.Unspecified
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            rootRule?.let { currentRule ->
                                val json = Json.encodeToString(currentRule)
                                db.saveRule(AppRuleConfig(packageName, json))
                            }
                            onNavigateBack()
                        }
                    }) {
                        Icon(painter = painterResource(R.drawable.arrow_back_24px), contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            RuleNodeView(
                node = rootRule!!,
                onNodeUpdate = { newNode -> rootRule = newNode },
                onDelete = { /* 根节点不允许删除，所以这里留空 */ },
                isRoot = true
            )
        }
    }
}

@Composable
fun RuleNodeView(
    node: RuleNode,
    onNodeUpdate: (RuleNode) -> Unit,
    onDelete: () -> Unit,
    isRoot: Boolean = false
) {
    when (node) {
        is RuleNode.Group -> {
            var isExpanded by remember { mutableStateOf(true) }

            Card(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = { isExpanded = !isExpanded }) {
                            Icon(
                                painter = if (isExpanded) painterResource(R.drawable.keyboard_control_key_24px) else painterResource(
                                    R.drawable.keyboard_arrow_down_24px
                                ),
                                contentDescription = "折叠/展开"
                            )
                        }
                        Text(
                            text = if (node.op == LogicOp.AND) "满足以下 所有 条件" else "满足以下 任意 条件",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary
                        )

                        TextButton(onClick = {
                            val newOp = if (node.op == LogicOp.AND) LogicOp.OR else LogicOp.AND
                            onNodeUpdate(node.copy(op = newOp))
                        }) {
                            Text("切换")
                        }

                        if (!isRoot) {
                            IconButton(onClick = onDelete) {
                                Icon(painter = painterResource(R.drawable.close_24px), contentDescription = "删除该组")
                            }
                        }
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    .padding(start = 12.dp)
                            ) {
                                node.children.forEachIndexed { index, childNode ->
                                    RuleNodeView(
                                        node = childNode,
                                        onNodeUpdate = { updatedChild ->
                                            val newChildren = node.children.toMutableList()
                                            newChildren[index] = updatedChild
                                            onNodeUpdate(node.copy(children = newChildren))
                                        },
                                        onDelete = {
                                            val newChildren = node.children.toMutableList()
                                            newChildren.removeAt(index)
                                            onNodeUpdate(node.copy(children = newChildren))
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Button(
                                    onClick = {
                                        val newChildren =
                                            node.children + RuleNode.Condition(RuleType.INCLUDES, RuleOn.TEXT, "")
                                        onNodeUpdate(node.copy(children = newChildren))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("加条件", style = MaterialTheme.typography.bodySmall)
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                FilledTonalButton(
                                    onClick = {
                                        val newChildren = node.children + RuleNode.Group(LogicOp.AND, emptyList())
                                        onNodeUpdate(node.copy(children = newChildren))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("嵌套组", style = MaterialTheme.typography.bodySmall)
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                OutlinedButton(
                                    onClick = {
                                        val newNotNode =
                                            RuleNode.Not(RuleNode.Condition(RuleType.INCLUDES, RuleOn.TEXT, ""))
                                        val newChildren = node.children + newNotNode
                                        onNodeUpdate(node.copy(children = newChildren))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        "加例外",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                    }


                }
            }
        }

        is RuleNode.Condition -> {
            Column(
                modifier = Modifier
                    .padding(vertical = 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            val newOn = if (node.ruleOn == RuleOn.TEXT) {
                                RuleOn.TITLE
                            } else {
                                RuleOn.TEXT
                            }
                            onNodeUpdate(node.copy(ruleOn = newOn))
                        },
                        modifier = Modifier.width(80.dp)
                    ) {
                        Text(
                            text = when (node.ruleOn) {
                                RuleOn.TITLE -> "标题"
                                RuleOn.TEXT -> "文本"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            val types = RuleType.entries.toTypedArray()
                            val nextIndex = (types.indexOf(node.ruleType) + 1) % types.size
                            onNodeUpdate(node.copy(ruleType = types[nextIndex]))
                        },
                        modifier = Modifier.width(110.dp)
                    ) {
                        Text(
                            text = when (node.ruleType) {
                                RuleType.STARTS_WITH -> "开头是"
                                RuleType.ENDS_WITH -> "结尾是"
                                RuleType.INCLUDES -> "包含"
                                RuleType.EQUALS_TO -> "等于"
                                RuleType.REGEX -> "正则匹配"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(onClick = onDelete) {
                        Icon(
                            painter = painterResource(R.drawable.delete_24px),
                            contentDescription = "删除条件",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    OutlinedTextField(
                        value = node.keyword,
                        onValueChange = { newText ->
                            onNodeUpdate(node.copy(keyword = newText))
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("关键词...") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }

        is RuleNode.Not -> {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "绝不能满足以下条件 (排除项)",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.error
                        )

                        IconButton(onClick = onDelete) {
                            Icon(
                                painter = painterResource(R.drawable.close_24px),
                                contentDescription = "删除排除项",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp)
                            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                            .padding(start = 12.dp)
                    ) {
                        RuleNodeView(
                            node = node.child,
                            onNodeUpdate = { updatedChild ->
                                onNodeUpdate(node.copy(child = updatedChild))
                            },
                            onDelete = {
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}