package com.example.janmanager.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janmanager.data.local.entity.ProductGroup
import com.example.janmanager.data.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupListViewModel @Inject constructor(
    private val groupRepository: GroupRepository
) : ViewModel() {

    val allGroups: StateFlow<List<ProductGroup>> = groupRepository.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createGroup(name: String, color: Int, endDate: Long, memo: String) {
        viewModelScope.launch {
            val group = ProductGroup(
                groupName = name,
                tagColor = color,
                startDate = System.currentTimeMillis(),
                endDate = endDate,
                memo = memo,
                isActive = true
            )
            groupRepository.createGroup(group)
        }
    }

    fun deleteGroup(group: ProductGroup) {
        viewModelScope.launch {
            groupRepository.updateGroup(group.copy(isActive = false))
        }
    }
}
