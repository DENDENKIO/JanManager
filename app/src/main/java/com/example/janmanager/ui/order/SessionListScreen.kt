package com.example.janmanager.ui.order

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.janmanager.data.local.entity.ScanSession
import com.example.janmanager.data.local.entity.SessionStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    onNavigateToScan: (Long) -> Unit,
    onNavigateToList: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SessionListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("発注セッション管理") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showCreateDialog(true) }) {
                Icon(Icons.Default.Add, contentDescription = "新規セッション")
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.sessions) { session ->
                    SessionItem(
                        session = session,
                        dateText = dateFormat.format(Date(session.id)), // ID is timestamp for simplicity now
                        onClick = {
                            if (session.status == SessionStatus.OPEN) {
                                onNavigateToScan(session.id)
                            } else {
                                onNavigateToList(session.id)
                            }
                        },
                        onRename = { viewModel.showRenameDialog(session) },
                        onDelete = { viewModel.showDeleteConfirm(session) }
                    )
                }
            }
        }
    }

    // Dialogs
    if (uiState.showCreateDialog) {
        var name by remember { mutableStateOf("セッション ${dateFormat.format(Date())}") }
        AlertDialog(
            onDismissRequest = { viewModel.showCreateDialog(false) },
            title = { Text("新規セッション作成") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("セッション名") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.createSession(name) }) {
                    Text("作成")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showCreateDialog(false) }) {
                    Text("キャンセル")
                }
            }
        )
    }

    uiState.showRenameDialog?.let { session ->
        var name by remember { mutableStateOf(session.sessionName) }
        AlertDialog(
            onDismissRequest = { viewModel.showRenameDialog(null) },
            title = { Text("セッション名変更") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("新しい名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.renameSession(session, name) }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showRenameDialog(null) }) {
                    Text("キャンセル")
                }
            }
        )
    }

    uiState.showDeleteConfirm?.let { session ->
        AlertDialog(
            onDismissRequest = { viewModel.showDeleteConfirm(null) },
            title = { Text("セッション削除") },
            text = { Text("「${session.sessionName}」を元に戻せません。このセッションとスキャンデータをすべて削除しますか？") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteSession(session) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showDeleteConfirm(null) }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

@Composable
fun SessionItem(
    session: ScanSession,
    dateText: String,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.sessionName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dateText,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = if (session.status == SessionStatus.OPEN) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = if (session.status == SessionStatus.OPEN) "進行中" else "完了済み",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            
            Row {
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = "名称変更", tint = Color.Gray)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "削除", tint = MaterialTheme.colorScheme.error)
                }
                Icon(
                    imageVector = if (session.status == SessionStatus.OPEN) 
                        Icons.Default.QrCodeScanner 
                    else 
                        Icons.Default.ListAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
