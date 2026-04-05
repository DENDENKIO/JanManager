package com.example.janmanager.ui.product

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.janmanager.data.local.entity.ProductStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    onNavigateToDetail: (String) -> Unit,
    viewModel: ProductListViewModel = hiltViewModel()
) {
    val query by viewModel.searchQuery.collectAsState()
    val products by viewModel.productsList.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()

    val filteredProducts = if (statusFilter != null) {
        products.filter { it.status == statusFilter }
    } else {
        products
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("商品一覧") },
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
        ) {
            TextField(
                value = query,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("検索...") },
                singleLine = true
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredProducts) { product ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable { onNavigateToDetail(product.janCode) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(product.janCode, style = MaterialTheme.typography.labelLarge)
                                // Status Badge Simulation
                                Text(
                                    text = product.status.name,
                                    color = when (product.status) {
                                        ProductStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                                        ProductStatus.DISCONTINUED -> MaterialTheme.colorScheme.error
                                        ProductStatus.RENEWED -> MaterialTheme.colorScheme.secondary
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Text(product.productName, style = MaterialTheme.typography.titleMedium)
                            Text(product.makerName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
