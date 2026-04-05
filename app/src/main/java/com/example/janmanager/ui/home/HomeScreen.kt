package com.example.janmanager.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            Text("メインメニュー", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // Navigation Buttons
            NavigationButton(
                text = "スキャン (3モード)",
                icon = Icons.Default.QrCodeScanner,
                onClick = onNavigateToScan
            )
            NavigationButton(
                text = "商品マスタ一覧",
                icon = Icons.Default.Inventory,
                onClick = onNavigateToProductList
            )
            NavigationButton(
                text = "AI商品情報一括取得",
                icon = Icons.Default.AutoFixHigh,
                onClick = onNavigateToAiFetch
            )
            NavigationButton(
                text = "発注支援（スキャン/一覧）",
                icon = Icons.Default.ShoppingCart,
                onClick = onNavigateToOrderScan
            )
            NavigationButton(
                text = "グループ商品管理",
                icon = Icons.AutoMirrored.Filled.Label,
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
            .height(64.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}
