package com.khedron.quill.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khedron.quill.data.preferences.PreferencesDataStore
import com.khedron.quill.data.preferences.QuillSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val verifySSL: Boolean = true,
    val saved: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val prefs: PreferencesDataStore) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = prefs.settings.first()
            _state.update { it.copy(serverUrl = s.serverUrl, username = s.username, password = s.password, verifySSL = s.verifySSL) }
        }
    }

    fun update(block: SettingsUiState.() -> SettingsUiState) = _state.update(block)

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            prefs.save(QuillSettings(
                serverUrl = _state.value.serverUrl.trim(),
                username = _state.value.username.trim(),
                password = _state.value.password,
                verifySSL = _state.value.verifySSL,
            ))
            onSaved()
        }
    }
}
