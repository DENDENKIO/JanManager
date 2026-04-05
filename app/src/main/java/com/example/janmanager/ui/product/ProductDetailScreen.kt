package com.example.janmanager.ui.product

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.janmanager.data.local.entity.PackageType
import com.example.janmanager.data.local.entity.ProductStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    janCode: String,
    onNavigateToDetail: (String) -> Unit,
    viewModel: ProductDetailViewModel = hiltViewModel()
) {
    val product by viewModel.product.collectAsState()
    val packages by viewModel.packages.collectAsState()

    var isEditing by remember { mutableStateOf(false) }

    LaunchedEffect(janCode) {
        viewModel.loadProduct(janCode)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("商品詳細") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        val p = product
        if (p == null) {
            Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                Text("読み込み中または未登録")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status Header
                Text(
                    text = "ステータス: ${p.status.name}",
                    color = when (p.status) {
                        ProductStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                        ProductStatus.DISCONTINUED -> MaterialTheme.colorScheme.error
                        ProductStatus.RENEWED -> MaterialTheme.colorScheme.secondary
                    },
                    style = MaterialTheme.typography.titleMedium
                )

                if (!isEditing) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("JAN: ${p.janCode}", style = MaterialTheme.typography.labelMedium)
                            Text("商品名: ${p.productName}", style = MaterialTheme.typography.titleLarge)
                            Text("メーカー: ${p.makerName}", style = MaterialTheme.typography.bodyLarge)
                            Text("規格: ${p.spec}", style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { isEditing = true }) {
                                Text("編集する")
                            }
                        }
                    }
                } else {
                    var editName by remember { mutableStateOf(p.productName) }
                    var editMaker by remember { mutableStateOf(p.makerName) }
                    var editSpec by remember { mutableStateOf(p.spec) }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextField(value = editName, onValueChange = { editName = it }, label = { Text("商品名") })
                            TextField(value = editMaker, onValueChange = { editMaker = it }, label = { Text("メーカー") })
                            TextField(value = editSpec, onValueChange = { editSpec = it }, label = { Text("規格") })
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    viewModel.saveProductChanges(p.copy(productName = editName, makerName = editMaker, spec = editSpec))
                                    isEditing = false
                                }) { Text("保存") }
                                Button(onClick = { isEditing = false }) { Text("キャンセル") }
                            }
                        }
                    }
                }

                Divider()

                // Package Management
                Text("包装単位", style = MaterialTheme.typography.titleMedium)
                packages.forEach { pack ->
                    Text("- ${pack.packageType.name} (${pack.quantityPerUnit}入): ${pack.barcode}")
                }
                var newPackBarcode by remember { mutableStateOf("") }
                var newPackQty by remember { mutableStateOf("12") }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(value = newPackBarcode, onValueChange = { newPackBarcode = it }, label = { Text("ITF/箱JAN") }, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        viewModel.addPackageUnit(newPackBarcode, PackageType.CASE, newPackQty.toIntOrNull() ?: 1)
                        newPackBarcode = ""
                    }) { Text("追加") }
                }

                Divider()

                // Renewal Management
                Text("リニューアル・終売", style = MaterialTheme.typography.titleMedium)
                if (p.renewedFromJan != null) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("旧品: ${p.renewedFromJan}")
                        androidx.compose.material3.TextButton(onClick = { onNavigateToDetail(p.renewedFromJan!!) }) {
                            Text("→ 詳細を見る")
                        }
                    }
                }
                if (p.renewedToJan != null) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("新品: ${p.renewedToJan}")
                        androidx.compose.material3.TextButton(onClick = { onNavigateToDetail(p.renewedToJan!!) }) {
                            Text("→ 詳細を見る")
                        }
                    }
                }
                
                var linkJan by remember { mutableStateOf("") }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(value = linkJan, onValueChange = { linkJan = it }, label = { Text("新JANを入力して紐づけ") }, modifier = Modifier.weight(1f))
                    Button(onClick = { 
                        viewModel.linkRenewalTarget(linkJan)
                        linkJan = ""
                    }) { Text("紐づける") }
                }

                Button(
                    onClick = { viewModel.discontinueProduct() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("終売として登録")
                }
            }
        }
    }
}
