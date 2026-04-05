package com.example.janmanager.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToScan: () -> Unit,
    onNavigateToProductList: () -> Unit,
    onNavigateToAiFetch: () -> Unit,
    onNavigateToOrderScan: () -> Unit,
    onNavigateToGroupList: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val totalCount by viewModel.totalProductsCount.collectAsState()
    val unfetchedCount by viewModel.unfetchedCount.collectAsState()
    val discontinuedCount by viewModel.discontinuedCount.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("JAN Manager") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Dashboard
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ダッシュボード", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("登録商品数: $totalCount")
                    Text("情報未取得: $unfetchedCount")
                    Text("終売品: $discontinuedCount")
                }
            }
            
            // Navigation Buttons
            NavigationButton(
                text = "スキャン (3モード)",
                icon = Icons.Default.PlayArrow,
                onClick = onNavigateToScan
            )
            NavigationButton(
                text = "商品マスタ一覧",
                icon = Icons.Default.List,
                onClick = onNavigateToProductList
            )
            NavigationButton(
                text = "AI商品情報取得",
                icon = Icons.Default.Settings, // Stub icon
                onClick = onNavigateToAiFetch
            )
            NavigationButton(
                text = "発注支援",
                icon = Icons.Default.ShoppingCart,
                onClick = onNavigateToOrderScan
            )
            NavigationButton(
                text = "グループ商品管理",
                icon = Icons.Default.List, // Stub icon
                onClick = onNavigateToGroupList
            )
            NavigationButton(
                text = "設定",
                icon = Icons.Default.Settings,
                onClick = onNavigateToSettings
            )
        }
    }
}

@Composable
fun NavigationButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Text(text)
        }
    }
}
