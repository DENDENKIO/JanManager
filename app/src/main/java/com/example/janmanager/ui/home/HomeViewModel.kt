package com.example.janmanager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janmanager.data.local.entity.ProductStatus
import com.example.janmanager.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    productRepository: ProductRepository
) : ViewModel() {

    val totalProductsCount: StateFlow<Int> = productRepository.searchProducts("", com.example.janmanager.data.repository.SearchType.JAN)
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val unfetchedCount: StateFlow<Int> = productRepository.getUnfetchedProducts()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val discontinuedCount: StateFlow<Int> = productRepository.getProductsByStatus(ProductStatus.DISCONTINUED)
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
