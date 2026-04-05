package com.example.janmanager.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janmanager.data.local.entity.ProductMaster
import com.example.janmanager.data.local.entity.ProductStatus
import com.example.janmanager.data.repository.GroupRepository
import com.example.janmanager.data.repository.ProductRepository
import com.example.janmanager.data.repository.SearchType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class ProductListViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val groupRepository: GroupRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchType = MutableStateFlow(SearchType.JAN)
    val searchType = _searchType.asStateFlow()

    private val _statusFilter = MutableStateFlow<ProductStatus?>(null)
    val statusFilter = _statusFilter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val products: StateFlow<List<ProductMaster>> = combine(
        _searchQuery,
        _searchType,
        _statusFilter
    ) { query, type, status ->
        Triple(query, type, status)
    }.flatMapLatest { (query, type, status) ->
        repository.searchProducts(query, type).map { list ->
            if (status == null) list else list.filter { it.status == status }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val groupsByJan: StateFlow<Map<String, List<Int>>> = groupRepository.getActiveGroups()
        .flatMapLatest { activeGroups ->
            if (activeGroups.isEmpty()) return@flatMapLatest flowOf(emptyMap<String, List<Int>>())
            
            val flows = activeGroups.map { group ->
                groupRepository.getGroupItems(group.id).map { items -> group to items }
            }
            
            combine(flows) { pairs ->
                val result = mutableMapOf<String, MutableList<Int>>()
                pairs.forEach { (group, items) ->
                    items.forEach { item ->
                        result.getOrPut(item.janCode) { mutableListOf() }.add(group.tagColor)
                    }
                }
                result
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun updateSearchType(type: SearchType) {
        _searchType.value = type
    }

    fun updateStatusFilter(status: ProductStatus?) {
        _statusFilter.value = status
    }
}
