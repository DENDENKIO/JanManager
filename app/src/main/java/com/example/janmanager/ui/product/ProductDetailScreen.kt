package com.example.janmanager.ui.product

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.janmanager.data.local.entity.PackageType
import com.example.janmanager.data.local.entity.PackageUnit
import com.example.janmanager.data.local.entity.ProductMaster
import com.example.janmanager.data.local.entity.ProductStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    janCode: String,
    onNavigateToDetail: (String) -> Unit,
    onNavigateBack: () -> Unit,
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
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        val p = product
        if (p == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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
                // Status Chip
                val (statusLabel, statusColor) = when (p.status) {
                    ProductStatus.ACTIVE -> "販売中" to MaterialTheme.colorScheme.primary
                    ProductStatus.DISCONTINUED -> "終売" to MaterialTheme.colorScheme.error
                    ProductStatus.RENEWED -> "更新済" to MaterialTheme.colorScheme.secondary
                }
                AssistChip(
                    onClick = {},
                    label = { Text(statusLabel) },
                    leadingIcon = { Icon(Icons.Default.Circle, contentDescription = null, tint = statusColor, modifier = Modifier.size(10.dp)) }
                )

                if (!isEditing) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            InfoRow("JAN", p.janCode)
                            InfoRow("商品名", p.productName)
                            InfoRow("かな", p.productNameKana)
                            InfoRow("メーカー", p.makerName)
                            InfoRow("規格", p.spec)
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { isEditing = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("情報を編集する")
                            }
                        }
                    }
                } else {
                    var editName by remember { mutableStateOf(p.productName) }
                    var editMaker by remember { mutableStateOf(p.makerName) }
                    var editSpec by remember { mutableStateOf(p.spec) }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = editName, 
                                onValueChange = { editName = it }, 
                                label = { Text("商品名") }, 
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = editMaker, 
                                onValueChange = { editMaker = it }, 
                                label = { Text("メーカー") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = editSpec, 
                                onValueChange = { editSpec = it }, 
                                label = { Text("規格") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        viewModel.saveProductChanges(p.copy(productName = editName, makerName = editMaker, spec = editSpec))
                                        isEditing = false
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("保存") }
                                OutlinedButton(
                                    onClick = { isEditing = false },
                                    modifier = Modifier.weight(1f)
                                ) { Text("キャンセル") }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Package Management
                Text("包装単位", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (packages.isEmpty()) {
                    Text(
                        "包装単位が登録されていません",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    packages.forEach { pack ->
                        PackageUnitRow(
                            pack = pack,
                            onDelete = { viewModel.deletePackageUnit(pack) }
                        )
                    }
                }

                NewPackageForm(onAdd = { barcode, type, qty -> 
                    viewModel.addPackageUnit(barcode, type, qty)
                })

                HorizontalDivider()

                // Renewal Management
                Text("リニューアル・終売管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (p.renewedFromJan != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("更新元: ${p.renewedFromJan}", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { onNavigateToDetail(p.renewedFromJan!!) }) {
                            Text("→ 詳細")
                        }
                    }
                }
                if (p.renewedToJan != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("更新先: ${p.renewedToJan}", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { onNavigateToDetail(p.renewedToJan!!) }) {
                            Text("→ 詳細")
                        }
                    }
                }
                
                var linkJan by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = linkJan, 
                    onValueChange = { linkJan = it }, 
                    label = { Text("新JANを紐づけ") }, 
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { 
                            if (linkJan.isNotEmpty()) {
                                viewModel.linkRenewalTarget(linkJan)
                                linkJan = ""
                            }
                        }) {
                            Text("紐づけ", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                )

                Button(
                    onClick = { viewModel.discontinueProduct() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("この商品を終売にする")
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium,
             color = MaterialTheme.colorScheme.onSurfaceVariant,
             modifier = Modifier.weight(0.35f))
        Text(value, style = MaterialTheme.typography.bodyMedium,
             modifier = Modifier.weight(0.65f),
             fontWeight = FontWeight.Medium)
    }
}

@Composable
fun PackageUnitRow(pack: PackageUnit, onDelete: () -> Unit) {
    val icon = when (pack.packageType) {
        PackageType.PIECE -> Icons.Default.CropFree
        PackageType.PACK -> Icons.Default.ViewModule
        PackageType.CASE -> Icons.Default.Inventory2
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = pack.packageType.displayName, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(pack.packageType.displayName, style = MaterialTheme.typography.labelMedium)
                Text(pack.barcode, style = MaterialTheme.typography.bodyMedium)
                if (pack.packageType != PackageType.PIECE) {
                    Text("${pack.quantityPerUnit}入", style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            var showConfirm by remember { mutableStateOf(false) }
            IconButton(onClick = { showConfirm = true }) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "削除",
                     tint = MaterialTheme.colorScheme.error)
            }
            if (showConfirm) {
                AlertDialog(
                    onDismissRequest = { showConfirm = false },
                    title = { Text("包装単位の削除") },
                    text = { Text("${pack.packageType.displayName} (${pack.barcode}) を削除しますか？") },
                    confirmButton = {
                        TextButton(onClick = { onDelete(); showConfirm = false }) { 
                            Text("削除", color = MaterialTheme.colorScheme.error) 
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirm = false }) { Text("キャンセル") }
                    }
                )
            }
        }
    }
}

@Composable
fun NewPackageForm(onAdd: (String, PackageType, Int) -> Unit) {
    var newPackBarcode by remember { mutableStateOf("") }
    var newPackQty by remember { mutableStateOf("") }
    var newPackType by remember { mutableStateOf(PackageType.CASE) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("新規包装単位を追加", style = MaterialTheme.typography.labelLarge)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PackageType.PIECE, PackageType.PACK, PackageType.CASE).forEach { type ->
                    FilterChip(
                        selected = newPackType == type,
                        onClick = { newPackType = type },
                        label = { Text(type.displayName) }
                    )
                }
            }

            OutlinedTextField(
                value = newPackBarcode,
                onValueChange = { newPackBarcode = it },
                label = { Text("バーコード") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (newPackType != PackageType.PIECE) {
                OutlinedTextField(
                    value = newPackQty,
                    onValueChange = { newPackQty = it.filter { c -> c.isDigit() } },
                    label = { Text("入数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Button(
                onClick = {
                    val qty = if (newPackType == PackageType.PIECE) 1 else newPackQty.toIntOrNull() ?: return@Button
                    onAdd(newPackBarcode, newPackType, qty)
                    newPackBarcode = ""
                    newPackQty = ""
                },
                enabled = newPackBarcode.isNotEmpty() && (newPackType == PackageType.PIECE || newPackQty.isNotEmpty()),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("追加する")
            }
        }
    }
}
