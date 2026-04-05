package com.example.janmanager.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.example.janmanager.ui.ai.AiFetchScreen
import com.example.janmanager.ui.group.GroupDetailScreen
import com.example.janmanager.ui.group.GroupListScreen
import com.example.janmanager.ui.group.GroupScanScreen
import com.example.janmanager.ui.home.HomeScreen
import com.example.janmanager.ui.order.OrderListScreen
import com.example.janmanager.ui.order.OrderScanScreen
import com.example.janmanager.ui.product.ProductDetailScreen
import com.example.janmanager.ui.product.ProductListScreen
import com.example.janmanager.ui.scan.ScanScreen
import com.example.janmanager.ui.settings.SettingsScreen

@Composable
fun JanManagerNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Route.Home
    ) {
        composable<Route.Home> {
            HomeScreen(
                onNavigateToScan = { navController.navigate(Route.Scan) },
                onNavigateToProductList = { navController.navigate(Route.ProductList) },
                onNavigateToAiFetch = { navController.navigate(Route.AiFetch) },
                onNavigateToOrderScan = { navController.navigate(Route.SessionList) },
                onNavigateToGroupList = { navController.navigate(Route.GroupList) },
                onNavigateToSettings = { navController.navigate(Route.Settings) }
            )
        }
        composable<Route.Scan> {
            ScanScreen(
                onNavigateToAiFetch = { navController.navigate(Route.AiFetch) }
            )
        }
        composable<Route.ProductList> {
            ProductListScreen(
                onNavigateToDetail = { janCode -> navController.navigate(Route.ProductDetail(janCode)) }
            )
        }
        composable<Route.ProductDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.ProductDetail>()
            ProductDetailScreen(
                janCode = route.janCode,
                onNavigateToDetail = { jan -> navController.navigate(Route.ProductDetail(jan)) }
            )
        }
        composable<Route.AiFetch> {
            AiFetchScreen()
        }
        composable<Route.SessionList> {
            // 後ほど作成する SessionListScreen
            com.example.janmanager.ui.order.SessionListScreen(
                onNavigateToScan = { sessionId -> navController.navigate(Route.OrderScan(sessionId)) },
                onNavigateToList = { sessionId -> navController.navigate(Route.OrderList(sessionId)) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<Route.OrderScan> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.OrderScan>()
            OrderScanScreen(
                sessionId = route.sessionId,
                onComplete = { navController.popBackStack() }
            )
        }
        composable<Route.OrderList> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.OrderList>()
            OrderListScreen(sessionId = route.sessionId)
        }
        composable<Route.GroupList> {
            GroupListScreen(
                onNavigateToGroupDetail = { groupId -> navController.navigate(Route.GroupDetail(groupId)) },
                onNavigateToGroupScan = { groupId -> navController.navigate(Route.GroupScan(groupId)) }
            )
        }
        composable<Route.GroupScan> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.GroupScan>()
            GroupScanScreen(
                groupId = route.groupId, 
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<Route.GroupDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.GroupDetail>()
            GroupDetailScreen(
                groupId = route.groupId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToScan = { groupId -> navController.navigate(Route.GroupScan(groupId)) }
            )
        }
        composable<Route.Settings> {
            SettingsScreen(
                onNavigateToAiFetch = { navController.navigate(Route.AiFetch) }
            )
        }
    }
}
