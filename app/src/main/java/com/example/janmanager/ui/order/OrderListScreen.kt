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
    var isFocusMode by remember { mutableStateOf(false) }

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
            ListModeContent(items, viewModel, padding)
        }
    }
}

@Composable
fun ListModeContent(
    items: List<OrderItemInfo>,
    viewModel: OrderListViewModel,
    padding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(items) { info ->
            OrderListItem(info, onToggle = { viewModel.toggleBarcodeVisibility(info.scanItem.id) })
            HorizontalDivider()
        }
    }
}

@Composable
fun OrderListItem(info: OrderItemInfo, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
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
                BarcodeGenerator.generateEan13Bitmap(info.scanItem.scannedBarcode, 300, 100)
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
        val window = (context as Activity).window
        val layoutParams = window.attributes
        val originalBrightness = layoutParams.screenBrightness
        layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        window.attributes = layoutParams

        onDispose {
            layoutParams.screenBrightness = originalBrightness
            window.attributes = layoutParams
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
                            BarcodeGenerator.generateEan13Bitmap(info.scanItem.scannedBarcode, 600, 200)
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
