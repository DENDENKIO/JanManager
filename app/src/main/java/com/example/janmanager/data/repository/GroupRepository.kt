package com.example.janmanager.data.repository

import com.example.janmanager.data.local.dao.ProductGroupDao
import com.example.janmanager.data.local.dao.ProductGroupItemDao
import com.example.janmanager.data.local.entity.ProductGroup
import com.example.janmanager.data.local.entity.ProductGroupItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    private val groupDao: ProductGroupDao,
    private val groupItemDao: ProductGroupItemDao
) {
    fun getAllGroups(): Flow<List<ProductGroup>> = groupDao.getAllGroups()
    
    fun getActiveGroups(): Flow<List<ProductGroup>> = groupDao.getGroupsByActiveStatus(true)

    suspend fun getGroupById(id: Long): ProductGroup? = groupDao.getGroupById(id)

    suspend fun createGroup(group: ProductGroup): Long = groupDao.insert(group)

    suspend fun updateGroup(group: ProductGroup) = groupDao.update(group)

    suspend fun deactivateExpiredGroups() {
        groupDao.deactivateExpiredGroups(System.currentTimeMillis())
    }

    fun getGroupItems(groupId: Long): Flow<List<ProductGroupItem>> = groupItemDao.getItemsForGroup(groupId)

    suspend fun addProductToGroup(groupId: Long, productId: Long, janCode: String): Boolean {
        if (!groupItemDao.isItemInGroup(groupId, janCode)) {
            groupItemDao.insert(ProductGroupItem(groupId = groupId, productId = productId, janCode = janCode))
            return true
        }
        return false // Already in group
    }
}
