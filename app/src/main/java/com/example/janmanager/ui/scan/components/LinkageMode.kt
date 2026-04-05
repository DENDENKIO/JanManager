package com.example.janmanager.ui.scan.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.janmanager.ui.scan.LinkageSlot
import com.example.janmanager.ui.scan.ScanViewModel

@Composable
fun LinkageMode(viewModel: ScanViewModel) {
    val oldJan by viewModel.linkageOldJan.collectAsState()
    val newJan by viewModel.linkageNewJan.collectAsState()
    val packJan by viewModel.linkagePackage.collectAsState()
    val activeSlot by viewModel.activeLinkageSlot.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("紐づけモード", style = MaterialTheme.typography.titleLarge)

        SlotCard(
            label = "旧JAN (リニューアル元)",
            value = oldJan,
            isActive = activeSlot == LinkageSlot.OLD_JAN,
            onClick = { viewModel.setLinkageSlot(LinkageSlot.OLD_JAN) }
        )

        SlotCard(
            label = "新JAN (リニューアル先)",
            value = newJan,
            isActive = activeSlot == LinkageSlot.NEW_JAN,
            onClick = { viewModel.setLinkageSlot(LinkageSlot.NEW_JAN) }
        )

        SlotCard(
            label = "パッケージ (箱/ケース)",
            value = packJan,
            isActive = activeSlot == LinkageSlot.PACKAGE,
            onClick = { viewModel.setLinkageSlot(LinkageSlot.PACKAGE) }
        )

        Button(
            onClick = { viewModel.executeLinkage() },
            modifier = Modifier.fillMaxWidth(),
            enabled = oldJan.isNotEmpty() || newJan.isNotEmpty() || packJan.isNotEmpty()
        ) {
            Text("確定して登録")
        }
    }
}

@Composable
fun SlotCard(label: String, value: String, isActive: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = value.ifEmpty { "未スキャン" }, 
                style = MaterialTheme.typography.bodyLarge,
                color = if (value.isEmpty()) Color.Gray else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
