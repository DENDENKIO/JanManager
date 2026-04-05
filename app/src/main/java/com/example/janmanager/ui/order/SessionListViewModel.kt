package com.example.janmanager.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janmanager.data.local.entity.ScanSession
import com.example.janmanager.data.local.entity.SessionStatus
import com.example.janmanager.data.repository.ScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionListUiState(
    val sessions: List<ScanSession> = emptyList(),
    val isLoading: Boolean = false,
    val showCreateDialog: Boolean = false,
    val showRenameDialog: ScanSession? = null,
    val showDeleteConfirm: ScanSession? = null
)

@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val scanRepository: ScanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionListUiState())
    val uiState: StateFlow<SessionListUiState> = _uiState.asStateFlow()

    init {
        loadSessions()
    }

    private fun loadSessions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            scanRepository.getAllSessions().collect { sessions ->
                _uiState.value = _uiState.value.copy(
                    sessions = sessions,
                    isLoading = false
                )
            }
        }
    }

    fun showCreateDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showCreateDialog = show)
    }

    fun createSession(name: String) {
        viewModelScope.launch {
            val sessionId = scanRepository.createSession(name)
            _uiState.value = _uiState.value.copy(showCreateDialog = false)
            // Navigation is handled by the UI calling a callback after creation if needed, 
            // or just letting the list update.
        }
    }

    fun showRenameDialog(session: ScanSession?) {
        _uiState.value = _uiState.value.copy(showRenameDialog = session)
    }

    fun renameSession(session: ScanSession, newName: String) {
        viewModelScope.launch {
            scanRepository.updateSession(session.copy(sessionName = newName))
            _uiState.value = _uiState.value.copy(showRenameDialog = null)
        }
    }

    fun showDeleteConfirm(session: ScanSession?) {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = session)
    }

    fun deleteSession(session: ScanSession) {
        viewModelScope.launch {
            scanRepository.deleteSession(session)
            _uiState.value = _uiState.value.copy(showDeleteConfirm = null)
        }
    }
}
