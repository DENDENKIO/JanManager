package com.example.janmanager.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.janmanager.data.local.entity.ProductGroup
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(
    onNavigateToGroupDetail: (Long) -> Unit,
    onNavigateToGroupScan: (Long) -> Unit,
    viewModel: GroupListViewModel = hiltViewModel()
) {
    val groups by viewModel.allGroups.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("グループ一覧") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "新規グループ")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val activeGroups = groups.filter { it.isActive }
            val inactiveGroups = groups.filter { !it.isActive }

            if (activeGroups.isNotEmpty()) {
                item { Text("有効なグループ", style = MaterialTheme.typography.titleMedium) }
                items(activeGroups) { group ->
                    GroupItem(
                        group = group,
                        onClick = { onNavigateToGroupDetail(group.id) },
                        onScanClick = { onNavigateToGroupScan(group.id) },
                        onDelete = { viewModel.deleteGroup(group) }
                    )
                }
            }

            if (inactiveGroups.isNotEmpty()) {
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item { Text("終了済みグループ", style = MaterialTheme.typography.titleMedium, color = Color.Gray) }
                items(inactiveGroups) { group ->
                    GroupItem(
                        group = group,
                        onClick = { onNavigateToGroupDetail(group.id) },
                        onScanClick = null,
                        onDelete = null
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, color, endDate, memo ->
                viewModel.createGroup(name, color, endDate, memo)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun GroupItem(
    group: ProductGroup,
    onClick: () -> Unit,
    onScanClick: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    val dateStr = sdf.format(Date(group.endDate))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (group.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Color(group.tagColor), MaterialTheme.shapes.small)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(group.groupName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("終了日: $dateStr", style = MaterialTheme.typography.bodySmall)
            }
            if (onScanClick != null) {
                Button(onClick = onScanClick) {
                    Text("スキャン追加")
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "削除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Long, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(Color.Blue) }
    var endDate by remember { mutableStateOf(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L) } // Default 1 week

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新規グループ作成") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("グループ名") })
                OutlinedTextField(value = memo, onValueChange = { memo = it }, label = { Text("メモ") })
                
                Text("タグ色選択")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Color.Red, Color.Blue, Color.Green, Color.Yellow, Color.Magenta).forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(c, MaterialTheme.shapes.small)
                                .clickable { color = c }
                                .padding(4.dp)
                        ) {
                            if (color == c) {
                                Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.5f)))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, color.toArgb(), endDate, memo) }) {
                Text("作成")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
