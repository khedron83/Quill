package com.khedron.quill.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khedron.quill.data.caldav.CalDAVClient
import com.khedron.quill.data.preferences.PreferencesDataStore
import com.khedron.quill.domain.model.QuillTask
import com.khedron.quill.domain.model.TaskCalendar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class TasksUiState(
    val calendars: List<TaskCalendar> = emptyList(),
    val selectedCalendar: TaskCalendar? = null,
    val tasks: List<QuillTask> = emptyList(),
    val isLoading: Boolean = false,
    val isUnconfigured: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val caldav: CalDAVClient,
    private val prefs: PreferencesDataStore,
) : ViewModel() {

    private val _state = MutableStateFlow(TasksUiState())
    val state: StateFlow<TasksUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { loadCalendars() }
    }

    private suspend fun configure(): Boolean {
        val s = prefs.settings.first()
        if (s.serverUrl.isBlank() || s.username.isBlank()) {
            _state.update { it.copy(isUnconfigured = true, isLoading = false) }
            return false
        }
        caldav.configure(s.serverUrl, s.username, s.password, s.verifySSL)
        return true
    }

    suspend fun loadCalendars() {
        _state.update { it.copy(isLoading = true, error = null, isUnconfigured = false) }
        if (!configure()) return
        try {
            val calendars = caldav.getCalendars()
            val selected = _state.value.selectedCalendar
                ?.let { prev -> calendars.find { it.url == prev.url } }
                ?: calendars.firstOrNull()
            _state.update { it.copy(calendars = calendars, selectedCalendar = selected, isLoading = selected == null) }
            selected?.let { loadTasks(it) }
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = e.message) }
        }
    }

    fun selectCalendar(calendar: TaskCalendar) {
        _state.update { it.copy(selectedCalendar = calendar, tasks = emptyList()) }
        viewModelScope.launch { loadTasks(calendar) }
    }

    private suspend fun loadTasks(calendar: TaskCalendar) {
        _state.update { it.copy(isLoading = true) }
        try {
            val tasks = caldav.getTasks(calendar.url)
                .sortedWith(compareBy({ it.completed }, { it.dueDate }, { it.summary }))
            _state.update { it.copy(tasks = tasks, isLoading = false, error = null) }
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = e.message) }
        }
    }

    fun refresh() {
        viewModelScope.launch { loadCalendars() }
    }

    fun addTask(summary: String, description: String, dueDate: LocalDate?) {
        val cal = _state.value.selectedCalendar ?: return
        val task = QuillTask(uid = UUID.randomUUID().toString(), summary = summary, description = description, dueDate = dueDate)
        viewModelScope.launch {
            try {
                caldav.createTask(cal.url, task)
                loadTasks(cal)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun editTask(task: QuillTask, summary: String, description: String, dueDate: LocalDate?, completed: Boolean) {
        val cal = _state.value.selectedCalendar ?: return
        viewModelScope.launch {
            try {
                caldav.updateTask(task.copy(summary = summary, description = description, dueDate = dueDate, completed = completed))
                loadTasks(cal)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun toggleTask(task: QuillTask) {
        val cal = _state.value.selectedCalendar ?: return
        viewModelScope.launch {
            try {
                caldav.updateTask(task.copy(completed = !task.completed))
                loadTasks(cal)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteTask(task: QuillTask) {
        val cal = _state.value.selectedCalendar ?: return
        viewModelScope.launch {
            try {
                caldav.deleteTask(task)
                loadTasks(cal)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun createCalendar(name: String) {
        viewModelScope.launch {
            try {
                caldav.createCalendar(name)
                loadCalendars()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
