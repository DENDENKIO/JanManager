package com.example.janmanager.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janmanager.data.local.entity.ProductMaster
import com.example.janmanager.data.local.entity.ProductStatus
import com.example.janmanager.data.repository.ProductRepository
import com.example.janmanager.data.repository.SearchType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductListViewModel @Inject constructor(
    private val repository: ProductRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchType = MutableStateFlow(SearchType.JAN)
    val searchType = _searchType.asStateFlow()

    private val _statusFilter = MutableStateFlow<ProductStatus?>(null)
    val statusFilter = _statusFilter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val productsList: StateFlow<List<ProductMaster>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isEmpty()) {
                // Return all initially (using jan search with empty triggers wildcard in DAO)
                repository.searchProducts("", SearchType.JAN)
            } else {
                repository.searchProducts(query, _searchType.value)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSearchType(type: SearchType) {
        _searchType.value = type
        // Changing type might demand re-triggering search but flatMap expects query updates or we combine flows.
        // For simplicity in UI logic:
        val q = _searchQuery.value
        _searchQuery.value = ""
        _searchQuery.value = q
    }

    fun updateStatusFilter(status: ProductStatus?) {
        _statusFilter.value = status
    }
}
