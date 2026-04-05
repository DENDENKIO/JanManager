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
import com.example.janmanager.data.local.entity.ProductStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    onNavigateToDetail: (String) -> Unit,
    viewModel: ProductListViewModel = hiltViewModel()
) {
    val products by viewModel.products.collectAsState()
    val groupsByJan by viewModel.groupsByJan.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("商品マスタ一覧") })
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { 
                    searchQuery = it
                    viewModel.search(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("JAN, 商品名, メーカー...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

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
