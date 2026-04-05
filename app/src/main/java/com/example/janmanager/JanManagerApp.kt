package com.example.janmanager

import android.app.Application
import com.example.janmanager.data.repository.GroupRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class JanManagerApp : Application() {

    @Inject
    lateinit var groupRepository: GroupRepository

    override fun onCreate() {
        super.onCreate()
        
        // Deactivate expired groups on app start
        MainScope().launch {
            groupRepository.deactivateExpiredGroups()
        }
    }
}
