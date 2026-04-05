package com.example.janmanager.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data object Home : Route
    @Serializable data object Scan : Route
    @Serializable data object ProductList : Route
    @Serializable data class ProductDetail(val janCode: String) : Route
    @Serializable data object AiFetch : Route
    @Serializable data object SessionList : Route
    @Serializable data class OrderScan(val sessionId: Long) : Route
    @Serializable data class OrderList(val sessionId: Long) : Route
    @Serializable data object GroupList : Route
    @Serializable data class GroupScan(val groupId: Long) : Route
    @Serializable data class GroupDetail(val groupId: Long) : Route
    @Serializable data object Settings : Route
}
