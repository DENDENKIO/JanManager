package com.example.janmanager.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToScan: (Long) -> Unit,
    viewModel: GroupDetailViewModel = hiltViewModel()
) {
    val group by viewModel.group.collectAsState()
    val items by viewModel.items.collectAsState()
    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    LaunchedEffect(groupId) {
        viewModel.loadGroup(groupId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.groupName ?: "グループ詳細") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    if (group?.isActive == true) {
                        IconButton(onClick = { onNavigateToScan(groupId) }) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "スキャン追加")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Group Header Info
            group?.let { g ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(16.dp).background(Color(g.tagColor), MaterialTheme.shapes.extraSmall))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ステータス: ${if (g.isActive) "有効" else "終了"}", fontWeight = FontWeight.Bold)
                        }
                        Text("終了日: ${SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(g.endDate))}")
                        if (g.memo.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("メモ: ${g.memo}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Text(
                text = "登録商品一覧 (${items.size}件)",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { item ->
                    ListItem(
                        headlineContent = { Text(item.productName) },
                        supportingContent = { 
                            Column {
                                Text(item.janCode)
                                Text("${item.makerName} / ${item.spec}")
                            }
                        },
                        trailingContent = {
                            Text(sdf.format(Date(item.addedAt)), style = MaterialTheme.typography.labelSmall)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
