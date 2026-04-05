package com.example.janmanager.ui.scan.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.janmanager.ui.scan.LinkageSlot
import com.example.janmanager.ui.scan.ScanViewModel

@Composable
fun LinkageMode(viewModel: ScanViewModel) {
    val pieceJan by viewModel.linkagePieceJan.collectAsState()
    val packJan by viewModel.linkagePackJan.collectAsState()
    val caseJan by viewModel.linkageCaseJan.collectAsState()
    val packQty by viewModel.linkagePackQty.collectAsState()
    val caseQty by viewModel.linkageCaseQty.collectAsState()
    val activeSlot by viewModel.activeLinkageSlot.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("包装紐づけモード", style = MaterialTheme.typography.titleLarge)
        Text(
            "スキャンするスロットをタップして選択し、バーコードを読み取ってください。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PackageSlotCard(
            label = "単品",
            icon = Icons.Default.CropFree,
            value = pieceJan,
            isActive = activeSlot == LinkageSlot.PIECE,
            onClick = { viewModel.setLinkageSlot(LinkageSlot.PIECE) },
            onClear = { viewModel.clearLinkageSlot(LinkageSlot.PIECE) }
        )

        PackageSlotCard(
            label = "パック",
            icon = Icons.Default.ViewModule,
            value = packJan,
            isActive = activeSlot == LinkageSlot.PACK,
            onClick = { viewModel.setLinkageSlot(LinkageSlot.PACK) },
            onClear = { viewModel.clearLinkageSlot(LinkageSlot.PACK) },
            quantity = packQty,
            onQuantityChange = { viewModel.setPackQty(it) }
        )

        PackageSlotCard(
            label = "ケース",
            icon = Icons.Default.Inventory2,
            value = caseJan,
            isActive = activeSlot == LinkageSlot.CASE,
            onClick = { viewModel.setLinkageSlot(LinkageSlot.CASE) },
            onClear = { viewModel.clearLinkageSlot(LinkageSlot.CASE) },
            quantity = caseQty,
            onQuantityChange = { viewModel.setCaseQty(it) }
        )

        Spacer(modifier = Modifier.weight(1f))

        val canRegister = pieceJan.isNotEmpty() && (packJan.isNotEmpty() || caseJan.isNotEmpty())
        Button(
            onClick = { viewModel.executePackageLinkage() },
            modifier = Modifier.fillMaxWidth(),
            enabled = canRegister
        ) {
            Text("包装単位を登録する")
        }

        if (!canRegister) {
            Text(
                "※ 単品JANと、パックまたはケースのJANが必要です",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun PackageSlotCard(
    label: String,
    icon: ImageVector,
    value: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onClear: () -> Unit,
    quantity: Int? = null,
    onQuantityChange: ((Int) -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(
                    text = value.ifEmpty { if (isActive) "▶ スキャン待ち" else "未スキャン" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                if (quantity != null && onQuantityChange != null && value.isNotEmpty()) {
                    OutlinedTextField(
                        value = quantity.toString(),
                        onValueChange = { it.toIntOrNull()?.let(onQuantityChange) },
                        label = { Text("入数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        singleLine = true
                    )
                }
            }
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Close, contentDescription = "クリア",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
