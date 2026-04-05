package com.example.janmanager.ui.scan.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.janmanager.ui.scan.ScanViewModel

@Composable
fun ConfirmMode(viewModel: ScanViewModel) {
    val product by viewModel.confirmProduct.collectAsState()
    val lastBarcode by viewModel.lastConfirmBarcode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("確認モード", style = MaterialTheme.typography.titleLarge)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("スキャンされたJAN: $lastBarcode", style = MaterialTheme.typography.titleMedium)
                
                if (product != null) {
                    Text("商品名: ${product?.productName}", modifier = Modifier.padding(top = 8.dp))
                    Text("メーカー: ${product?.makerName}")
                    Text("規格: ${product?.spec}")
                } else if (lastBarcode.isNotEmpty()) {
                    Text("商品未登録", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        Button(
            onClick = { /* TODO: Open BottomSheet WebView in later phase */ },
            modifier = Modifier.fillMaxWidth(),
            enabled = lastBarcode.isNotEmpty() && product == null
        ) {
            Text("AI情報取得")
        }
    }
}
