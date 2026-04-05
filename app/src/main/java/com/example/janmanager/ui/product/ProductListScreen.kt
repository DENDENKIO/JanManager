package com.example.janmanager.ui.product

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.janmanager.data.local.entity.ProductMaster
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.example.janmanager.data.local.entity.ProductStatus
import com.example.janmanager.data.repository.SearchType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    onNavigateToDetail: (String) -> Unit,
    viewModel: ProductListViewModel = hiltViewModel()
) {
    val products by viewModel.products.collectAsState()
    val groupsByJan by viewModel.groupsByJan.collectAsState()
    val currentSearchType by viewModel.searchType.collectAsState()
    val currentStatusFilter by viewModel.statusFilter.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("商品マスタ一覧") })
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Search Type Selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchType.values().forEach { type ->
                    FilterChip(
                        selected = currentSearchType == type,
                        onClick = { viewModel.updateSearchType(type) },
                        label = {
                            Text(
                                when (type) {
                                    SearchType.JAN -> "JAN"
                                    SearchType.NAME_KANA -> "商品名"
                                    SearchType.MAKER -> "メーカー"
                                    SearchType.SPEC -> "規格"
                                }
                            )
                        }
                    )
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { 
                    searchQuery = it
                    viewModel.search(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("検索ワード入力...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            // Status Filter Selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = currentStatusFilter == null,
                    onClick = { viewModel.updateStatusFilter(null) },
                    label = { Text("すべて") }
                )
                ProductStatus.values().forEach { status ->
                    FilterChip(
                        selected = currentStatusFilter == status,
                        onClick = { viewModel.updateStatusFilter(status) },
                        label = {
                            Text(
                                when (status) {
                                    ProductStatus.ACTIVE -> "継続"
                                    ProductStatus.DISCONTINUED -> "終売"
                                    ProductStatus.RENEWED -> "更新済"
                                }
                            )
                        }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(products) { product ->
                    ProductListItem(
                        product = product,
                        tags = groupsByJan[product.janCode] ?: emptyList(),
                        onClick = { onNavigateToDetail(product.janCode) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun ProductListItem(
    product: ProductMaster,
    tags: List<Int>,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(product.productName.ifEmpty { "(商品名未設定)" })
                if (product.status == ProductStatus.DISCONTINUED) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp)
                    ) { Text("終売", color = Color.White) }
                }
            }
        },
        supportingContent = {
            Column {
                Text("${product.janCode} | ${product.makerName}")
                // Group Tags
                if (tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tags.forEach { colorInt ->
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(colorInt), MaterialTheme.shapes.extraSmall)
                            )
                        }
                    }
                }
            }
        },
        trailingContent = {
            if (!product.infoFetched) {
                Text("未取得", color = Color.Gray, fontSize = 12.sp)
            }
        }
    )
}
