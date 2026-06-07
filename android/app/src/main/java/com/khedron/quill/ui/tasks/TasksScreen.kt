package com.khedron.quill.ui.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khedron.quill.domain.model.QuillTask
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<QuillTask?>(null) }
    var showNewCalendarDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    state.error?.let { err ->
        LaunchedEffect(err) {
            // shown via snackbar host below
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Quill") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, "More")
                        }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("New Calendar") },
                                leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                                onClick = { showMoreMenu = false; showNewCalendarDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = { showMoreMenu = false; onOpenSettings() },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.selectedCalendar != null) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, "Add Task")
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Calendar chips ───────────────────────────────────────────────
            if (state.calendars.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.calendars) { cal ->
                        FilterChip(
                            selected = cal == state.selectedCalendar,
                            onClick = { viewModel.selectCalendar(cal) },
                            label = { Text(cal.name) },
                            leadingIcon = if (cal == state.selectedCalendar) ({
                                Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                            }) else null,
                        )
                    }
                }
                HorizontalDivider()
            }

            // ── Content ──────────────────────────────────────────────────────
            when {
                state.isUnconfigured -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Default.Cloud, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary)
                            Text("No server configured", style = MaterialTheme.typography.titleMedium)
                            Text("Add your Nextcloud details to get started.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                            Button(onClick = onOpenSettings) {
                                Icon(Icons.Default.Settings, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Open Settings")
                            }
                        }
                    }
                }
                state.isLoading && state.tasks.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.calendars.isEmpty() && !state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("No task calendars found", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { showNewCalendarDialog = true }) {
                                Text("Create a calendar")
                            }
                        }
                    }
                }
                state.tasks.isEmpty() && !state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No tasks. Tap + to add one.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                else -> {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(state.tasks, key = { it.uid }) { task ->
                            TaskItem(
                                task = task,
                                onToggle = { viewModel.toggleTask(task) },
                                onEdit = { editingTask = task },
                                onDelete = { viewModel.deleteTask(task) },
                            )
                        }
                    }
                }
            }

            state.error?.let { err ->
                Spacer(Modifier.weight(1f))
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::dismissError) { Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer) }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        TaskDialog(
            title = "New Task",
            onDismiss = { showAddDialog = false },
            onConfirm = { summary, description, dueDate, _ ->
                viewModel.addTask(summary, description, dueDate)
                showAddDialog = false
            },
        )
    }

    editingTask?.let { task ->
        TaskDialog(
            title = "Edit Task",
            initialSummary = task.summary,
            initialDescription = task.description,
            initialDue = task.dueDate,
            initialCompleted = task.completed,
            onDismiss = { editingTask = null },
            onConfirm = { summary, description, dueDate, completed ->
                viewModel.editTask(task, summary, description, dueDate, completed)
                editingTask = null
            },
        )
    }

    if (showNewCalendarDialog) {
        NewCalendarDialog(
            onDismiss = { showNewCalendarDialog = false },
            onConfirm = { name -> viewModel.createCalendar(name); showNewCalendarDialog = false },
        )
    }
}

@Composable
private fun TaskItem(
    task: QuillTask,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = task.completed, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    task.summary,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                    color = if (task.completed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.description.isNotBlank()) {
                    Text(task.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                task.dueDate?.let { due ->
                    val today = LocalDate.now()
                    val overdue = due.isBefore(today) && !task.completed
                    Text(
                        "Due ${due.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Options") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { showMenu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDialog(
    title: String,
    initialSummary: String = "",
    initialDescription: String = "",
    initialDue: LocalDate? = null,
    initialCompleted: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (summary: String, description: String, dueDate: LocalDate?, completed: Boolean) -> Unit,
) {
    var summary by remember { mutableStateOf(initialSummary) }
    var description by remember { mutableStateOf(initialDescription) }
    var hasDue by remember { mutableStateOf(initialDue != null) }
    var dueDate by remember { mutableStateOf(initialDue ?: LocalDate.now()) }
    var completed by remember { mutableStateOf(initialCompleted) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text("Summary *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = hasDue, onCheckedChange = { hasDue = it })
                    Text("Due date", style = MaterialTheme.typography.bodyMedium)
                    if (hasDue) {
                        TextButton(onClick = { showDatePicker = true }) {
                            Text(dueDate.format(DateTimeFormatter.ofPattern("d MMM yyyy")))
                        }
                    }
                }
                if (initialCompleted || completed) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(checked = completed, onCheckedChange = { completed = it })
                        Text("Completed", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (summary.isNotBlank()) onConfirm(summary.trim(), description.trim(), if (hasDue) dueDate else null, completed) },
                enabled = summary.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dueDate.toEpochDay() * 86_400_000L,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        dueDate = LocalDate.ofEpochDay(it / 86_400_000L)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun NewCalendarDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Calendar") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Calendar name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
