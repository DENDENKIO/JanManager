package com.example.janmanager.ui.ai.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.janmanager.data.local.entity.ProductMaster

@Composable
fun AiResultPreview(
    product: ProductMaster,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "JAN: ${product.janCode}", style = MaterialTheme.typography.labelMedium)
            
            if (product.spec == "Not Found") {
                Text(text = "商品情報は見つかりませんでした", color = MaterialTheme.colorScheme.error)
            } else {
                Text(text = product.productName, style = MaterialTheme.typography.titleMedium)
                Text(text = product.productNameKana, style = MaterialTheme.typography.bodySmall)
                Text(text = product.makerName, style = MaterialTheme.typography.bodyMedium)
                Text(text = product.spec, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
