package com.example.janmanager.ui.ai.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.janmanager.util.AiResponseData

@Composable
fun AiResultPreview(
    result: AiResponseData,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "取得結果プレビュー",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (result.not_found) {
                Text(
                    text = "商品が見つかりませんでした",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            } else {
                ResultItem(label = "JANコード", value = result.jan_code)
                ResultItem(label = "メーカー名", value = result.maker_name)
                ResultItem(label = "メーカー名(かな)", value = result.maker_name_kana)
                ResultItem(label = "商品名", value = result.product_name)
                ResultItem(label = "商品名(かな)", value = result.product_name_kana)
                ResultItem(label = "規格", value = result.spec)
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onReject) {
                    Text("破棄", color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onAccept) {
                    Text("取得済みを保存")
                }
            }
        }
    }
}

@Composable
private fun ResultItem(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value.ifEmpty { "---" },
            style = MaterialTheme.typography.bodyLarge,
            fontSize = 18.sp
        )
    }
}
