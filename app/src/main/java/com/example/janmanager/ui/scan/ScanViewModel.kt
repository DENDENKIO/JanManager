package com.example.janmanager.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janmanager.data.local.entity.BarcodeType
import com.example.janmanager.data.local.entity.ProductMaster
import com.example.janmanager.data.repository.ProductRepository
import com.example.janmanager.data.settings.SettingsDataStore
import com.example.janmanager.util.JanCodeUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ScanModeTab {
    CONTINUOUS, CONFIRM, LINKAGE
}

enum class LinkageSlot {
    OLD_JAN, NEW_JAN, PACKAGE
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    settingsDataStore: SettingsDataStore
) : ViewModel() {

    val isItfEnabled = settingsDataStore.isItfEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _currentTab = MutableStateFlow(ScanModeTab.CONTINUOUS)
    val currentTab = _currentTab.asStateFlow()

    // Continuous Mode State
    private val _recentlyScanned = MutableStateFlow<List<String>>(emptyList())
    val recentlyScanned = _recentlyScanned.asStateFlow()

    // Confirm Mode State
    private val _confirmProduct = MutableStateFlow<ProductMaster?>(null)
    val confirmProduct = _confirmProduct.asStateFlow()
    private val _lastConfirmBarcode = MutableStateFlow("")
    val lastConfirmBarcode = _lastConfirmBarcode.asStateFlow()

    // Linkage Mode State
    private val _activeLinkageSlot = MutableStateFlow(LinkageSlot.OLD_JAN)
    val activeLinkageSlot = _activeLinkageSlot.asStateFlow()
    
    private val _linkageOldJan = MutableStateFlow("")
    val linkageOldJan = _linkageOldJan.asStateFlow()
    
    private val _linkageNewJan = MutableStateFlow("")
    val linkageNewJan = _linkageNewJan.asStateFlow()

    private val _linkagePackage = MutableStateFlow("")
    val linkagePackage = _linkagePackage.asStateFlow()

    fun setTab(tab: ScanModeTab) {
        _currentTab.value = tab
    }
    
    fun setLinkageSlot(slot: LinkageSlot) {
        _activeLinkageSlot.value = slot
    }

    fun processBarcode(barcode: String) {
        val type = JanCodeUtil.detectCodeType(barcode)
        
        // ITF Control
        if (!isItfEnabled.value && type == BarcodeType.ITF14) {
            return // Ignore ITF if disabled
        }

        // Convert ITF to JAN if necessary or keep it based on modes
        val normalizedBarcode = if (type == BarcodeType.ITF14) JanCodeUtil.itfToJan(barcode) else barcode

        when (_currentTab.value) {
            ScanModeTab.CONTINUOUS -> {
                val list = _recentlyScanned.value.toMutableList()
                list.add(0, normalizedBarcode)
                _recentlyScanned.value = list.take(50) // Keep last 50
                // In a real scenario, this is where we'd immediately log or add to a session
            }
            ScanModeTab.CONFIRM -> {
                _lastConfirmBarcode.value = normalizedBarcode
                viewModelScope.launch {
                    _confirmProduct.value = productRepository.getProductByJan(normalizedBarcode)
                }
            }
            ScanModeTab.LINKAGE -> {
                when (_activeLinkageSlot.value) {
                    LinkageSlot.OLD_JAN -> {
                        _linkageOldJan.value = normalizedBarcode
                        _activeLinkageSlot.value = LinkageSlot.NEW_JAN
                    }
                    LinkageSlot.NEW_JAN -> {
                        _linkageNewJan.value = normalizedBarcode
                        _activeLinkageSlot.value = LinkageSlot.PACKAGE
                    }
                    LinkageSlot.PACKAGE -> {
                        _linkagePackage.value = normalizedBarcode
                    }
                }
            }
        }
    }
    
    fun executeLinkage() {
        viewModelScope.launch {
            if (_linkageOldJan.value.isNotEmpty() && _linkageNewJan.value.isNotEmpty()) {
                productRepository.linkRenewal(_linkageOldJan.value, _linkageNewJan.value)
            }
            // Package logic could be added here
            
            // clear
            _linkageOldJan.value = ""
            _linkageNewJan.value = ""
            _linkagePackage.value = ""
            _activeLinkageSlot.value = LinkageSlot.OLD_JAN
        }
    }
}
