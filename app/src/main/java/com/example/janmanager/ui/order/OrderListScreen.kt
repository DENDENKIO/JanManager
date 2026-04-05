package com.example.janmanager.ui.order

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.janmanager.util.BarcodeGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderListScreen(
    sessionId: Long,
    viewModel: OrderListViewModel = hiltViewModel()
) {
    val session by viewModel.session.collectAsState()
    val items by viewModel.items.collectAsState()
    val context = LocalContext.current
    var isFocusMode by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<OrderItemInfo?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { viewModel.exportCsv(context, it) }
    }

    LaunchedEffect(sessionId) {
        viewModel.loadSession(sessionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(session?.sessionName ?: "発注一覧")
                        val progress = viewModel.getProgress()
                        Text("${progress.first}/${progress.second} 完了", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(onClick = { exportLauncher.launch("order_history_${session?.sessionName ?: sessionId}.csv") }) {
                        Icon(Icons.Default.Download, contentDescription = "CSV出力")
                    }
                    IconButton(onClick = { isFocusMode = !isFocusMode }) {
                        Icon(
                            if (isFocusMode) Icons.Default.List else Icons.Default.ViewCarousel,
                            contentDescription = "表示切替"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (isFocusMode) {
            FocusModeContent(items, viewModel, padding)
        } else {
            ListModeContent(items, viewModel, padding, onDeleteRequest = { itemToDelete = it })
        }

        itemToDelete?.let { info ->
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                title = { Text("明細の削除") },
                text = { Text("「${info.product?.productName ?: info.scanItem.scannedBarcode}」を一覧から削除しますか？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteItem(info)
                            itemToDelete = null
                        }
                    ) {
                        Text("削除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDelete = null }) {
                        Text("キャンセル")
                    }
                }
            )
        }
    }
}

@Composable
fun ListModeContent(
    items: List<OrderItemInfo>,
    viewModel: OrderListViewModel,
    padding: PaddingValues,
    onDeleteRequest: (OrderItemInfo) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(items) { info ->
            OrderListItem(
                info = info, 
                onToggle = { viewModel.toggleBarcodeVisibility(info.scanItem.id) },
                onDeleteRequest = { onDeleteRequest(info) }
            )
            HorizontalDivider()
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OrderListItem(info: OrderItemInfo, onToggle: () -> Unit, onDeleteRequest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onToggle() },
                onLongClick = { onDeleteRequest() }
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(info.product?.productName ?: "不明な商品", fontWeight = FontWeight.Bold)
            Text(info.scanItem.scannedBarcode, style = MaterialTheme.typography.bodySmall)
            Text("${info.product?.makerName} / ${info.product?.spec}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }

        if (info.isVisible) {
            val barcodeBitmap = remember(info.scanItem.scannedBarcode) {
                BarcodeGenerator.generateBarcodeBitmap(info.scanItem.scannedBarcode, 300, 100)
            }
            barcodeBitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Barcode",
                    modifier = Modifier.width(150.dp).height(50.dp),
                    contentScale = ContentScale.FillBounds
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .width(150.dp)
                    .height(50.dp)
                    .background(Color.LightGray.copy(alpha = 0.5f), MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Text("済", fontWeight = FontWeight.Bold, color = Color.Gray)
            }
        }
    }
}

@Composable
fun FocusModeContent(
    items: List<OrderItemInfo>,
    viewModel: OrderListViewModel,
    padding: PaddingValues
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { items.size })

    DisposableEffect(Unit) {
        val activity = context as? androidx.activity.ComponentActivity
        val window = activity?.window
        val layoutParams = window?.attributes
        val originalBrightness = layoutParams?.screenBrightness ?: -1f
        
        window?.let { w ->
            w.attributes = w.attributes.also {
                it.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            }
        }

        onDispose {
            window?.let { w ->
                w.attributes = w.attributes.also {
                    it.screenBrightness = originalBrightness
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val info = items[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(info.product?.productName ?: "不明な商品", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(info.scanItem.scannedBarcode, fontSize = 18.sp, color = Color.Gray)
                Text("${info.product?.makerName} | ${info.product?.spec}", fontSize = 16.sp)
                
                Spacer(modifier = Modifier.height(48.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clickable { viewModel.toggleBarcodeVisibility(info.scanItem.id) },
                    contentAlignment = Alignment.Center
                ) {
                    if (info.isVisible) {
                        val barcodeBitmap = remember(info.scanItem.scannedBarcode) {
                            BarcodeGenerator.generateBarcodeBitmap(info.scanItem.scannedBarcode, 600, 200)
                        }
                        barcodeBitmap?.let {
                            Image(
                                bitmap = it,
                                contentDescription = "Barcode Large",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.LightGray.copy(alpha = 0.3f), MaterialTheme.shapes.medium),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("スキャン済み (タップで再表示)", color = Color.Gray)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                Text("${page + 1} / ${items.size}", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
