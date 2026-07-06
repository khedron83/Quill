"""Main application window."""

import uuid

from PySide6.QtWidgets import (
    QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QListWidget, QListWidgetItem, QPushButton, QMessageBox,
    QDialog, QLabel, QLineEdit, QCheckBox, QTextEdit,
    QFormLayout, QStatusBar, QDateEdit, QInputDialog,
    QFrame, QSizePolicy,
)
from PySide6.QtCore import Qt, QDate
from PySide6.QtGui import QAction, QFont
from src.core.config import Config
from src.core.caldav_client import CalDAVClient, Task


def _title_label(text: str) -> QLabel:
    label = QLabel(text)
    font = label.font()
    font.setPointSize(font.pointSize() + 3)
    font.setBold(True)
    label.setFont(font)
    return label


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.config = Config()
        self.caldav_client: CalDAVClient | None = None
        self._current_calendar_url: str | None = None
        self.setWindowTitle("Quill")
        self.setMinimumSize(860, 580)
        self._setup_ui()
        self._init()

    def _setup_ui(self):
        mb = self.menuBar()
        file_menu = mb.addMenu("&File")
        settings_act = QAction("&Settings", self)
        settings_act.triggered.connect(self._open_settings)
        file_menu.addAction(settings_act)
        file_menu.addSeparator()
        quit_act = QAction("&Quit", self)
        quit_act.triggered.connect(self.close)
        file_menu.addAction(quit_act)
        help_menu = mb.addMenu("&Help")
        about_act = QAction("&About Quill", self)
        about_act.triggered.connect(self._show_about)
        help_menu.addAction(about_act)

        central = QWidget()
        self.setCentralWidget(central)
        root = QHBoxLayout(central)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)

        # ── Sidebar ───────────────────────────────────────────────────
        sidebar = QWidget()
        sidebar.setFixedWidth(220)
        sl = QVBoxLayout(sidebar)
        sl.setContentsMargins(0, 0, 0, 0)
        sl.setSpacing(0)

        cal_label = QLabel("CALENDARS")
        cal_font = cal_label.font()
        cal_font.setBold(True)
        cal_font.setPointSize(cal_font.pointSize() - 2)
        cal_label.setFont(cal_font)
        cal_label.setContentsMargins(14, 18, 14, 4)
        sl.addWidget(cal_label)

        self._calendar_list = QListWidget()
        self._calendar_list.itemClicked.connect(self._on_calendar_selected)
        sl.addWidget(self._calendar_list, 1)

        new_cal_btn = QPushButton("＋  New Calendar")
        new_cal_btn.clicked.connect(self._add_calendar)
        sl.addWidget(new_cal_btn)
        root.addWidget(sidebar)

        # ── Content pane ──────────────────────────────────────────────
        content = QWidget()
        cl = QVBoxLayout(content)
        cl.setContentsMargins(20, 16, 20, 16)
        cl.setSpacing(12)

        self._task_list = QListWidget()
        self._task_list.itemDoubleClicked.connect(self._edit_task)
        self._task_list.itemClicked.connect(lambda _: self._set_task_btns_enabled(True))
        cl.addWidget(self._task_list)

        btn_row = QHBoxLayout()
        btn_row.setSpacing(8)
        self._add_btn = QPushButton("＋  Add Task")
        self._add_btn.setEnabled(False)

        self._edit_btn = QPushButton("Edit")
        self._toggle_btn = QPushButton("Toggle Done")
        self._delete_btn = QPushButton("Delete")

        self._add_btn.clicked.connect(self._add_task)
        self._edit_btn.clicked.connect(self._edit_task)
        self._toggle_btn.clicked.connect(self._toggle_task)
        self._delete_btn.clicked.connect(self._delete_task)

        btn_row.addWidget(self._add_btn)
        btn_row.addStretch()
        btn_row.addWidget(self._edit_btn)
        btn_row.addWidget(self._toggle_btn)
        btn_row.addWidget(self._delete_btn)
        cl.addLayout(btn_row)

        self._set_task_btns_enabled(False)
        root.addWidget(content, 1)

        self.setStatusBar(QStatusBar())

    def _set_task_btns_enabled(self, enabled: bool):
        for btn in (self._edit_btn, self._toggle_btn, self._delete_btn):
            btn.setEnabled(enabled)

    def _init(self):
        if not self.config.server_url or not self.config.username:
            self.statusBar().showMessage("Configure your Nextcloud server in Settings")
            return
        self._connect_caldav()
        self._load_calendars()

    def _connect_caldav(self):
        self.caldav_client = CalDAVClient(
            url=self.config.server_url,
            username=self.config.username,
            password=self.config.password,
            verify_ssl=self.config.verify_ssl,
        )
        self.statusBar().showMessage("Connected to Nextcloud")

    def _load_calendars(self):
        if not self.caldav_client:
            return
        cals = self.caldav_client.get_calendars()
        self._calendar_list.clear()
        for name, url in cals:
            item = QListWidgetItem(name)
            item.setData(Qt.UserRole, url)
            self._calendar_list.addItem(item)

    def _on_calendar_selected(self, item: QListWidgetItem):
        self._current_calendar_url = item.data(Qt.UserRole)
        self._add_btn.setEnabled(True)
        self._load_tasks()

    def _load_tasks(self):
        if not self.caldav_client or not self._current_calendar_url:
            return
        tasks = self.caldav_client.get_tasks(self._current_calendar_url)
        self._task_list.clear()
        self._set_task_btns_enabled(False)
        for task in tasks:
            item = QListWidgetItem(f"  {'✓' if task.completed else '○'}  {task.summary}")
            item.setData(Qt.UserRole, task)
            if task.completed:
                font = item.font()
                font.setStrikeOut(True)
                item.setFont(font)
                item.setForeground(self._task_list.palette().placeholderText())
            self._task_list.addItem(item)

    def _selected_task(self) -> Task | None:
        item = self._task_list.currentItem()
        return item.data(Qt.UserRole) if item else None

    def _add_task(self):
        if not self._current_calendar_url:
            return
        dlg = TaskDialog(parent=self)
        if not dlg.exec():
            return
        task = dlg.task()
        task.uid = str(uuid.uuid4())
        if self.caldav_client.create_task(self._current_calendar_url, task):
            self.statusBar().showMessage(f"Added: {task.summary}")
            self._load_tasks()
        else:
            QMessageBox.warning(self, "Error", "Failed to create task.")

    def _edit_task(self):
        task = self._selected_task()
        if not task:
            return
        dlg = TaskDialog(task=task, parent=self)
        if not dlg.exec():
            return
        updated = dlg.task()
        updated.uid = task.uid
        if self.caldav_client.update_task(self._current_calendar_url, updated):
            self.statusBar().showMessage(f"Saved: {updated.summary}")
            self._load_tasks()
        else:
            QMessageBox.warning(self, "Error", "Failed to update task.")

    def _toggle_task(self):
        task = self._selected_task()
        if not task:
            return
        task.completed = not task.completed
        if self.caldav_client.update_task(self._current_calendar_url, task):
            self.statusBar().showMessage(f"{'Completed' if task.completed else 'Reopened'}: {task.summary}")
            self._load_tasks()
        else:
            QMessageBox.warning(self, "Error", "Failed to update task.")

    def _delete_task(self):
        task = self._selected_task()
        if not task:
            return
        reply = QMessageBox.question(
            self, "Delete Task",
            f'Delete "{task.summary}"?',
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
        )
        if reply != QMessageBox.StandardButton.Yes:
            return
        if self.caldav_client.delete_task(self._current_calendar_url, task.uid):
            self.statusBar().showMessage(f"Deleted: {task.summary}")
            self._load_tasks()
        else:
            QMessageBox.warning(self, "Error", "Failed to delete task.")

    def _add_calendar(self):
        if not self.caldav_client:
            return
        name, ok = QInputDialog.getText(self, "New Calendar", "Calendar name:")
        if not ok or not name.strip():
            return
        if self.caldav_client.create_calendar(name.strip()):
            self.statusBar().showMessage(f"Created calendar: {name.strip()}")
            self._load_calendars()
        else:
            QMessageBox.warning(self, "Error", "Failed to create calendar.")

    def _open_settings(self):
        dlg = SettingsDialog(self.config, self)
        if dlg.exec():
            self._connect_caldav()
            self._load_calendars()

    def _show_about(self):
        QMessageBox.about(
            self, "About Quill",
            "<b>Quill</b> v1.0<br><br>"
            "A task manager with Nextcloud CalDAV sync.<br>"
            "Organize tasks into calendars with subtask support.<br><br>"
            "Licensed under the GNU General Public License v3.0.",
        )


class TaskDialog(QDialog):
    def __init__(self, task: Task | None = None, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Edit Task" if task else "New Task")
        self.setMinimumWidth(420)
        self._build_ui(task)

    def _build_ui(self, task: Task | None):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 20, 24, 20)
        layout.setSpacing(16)

        title = _title_label("Edit Task" if task else "New Task")
        layout.addWidget(title)

        sep = QFrame()
        sep.setFrameShape(QFrame.Shape.HLine)
        sep.setFrameShadow(QFrame.Shadow.Sunken)
        layout.addWidget(sep)

        form = QFormLayout()
        form.setSpacing(10)
        form.setLabelAlignment(Qt.AlignmentFlag.AlignRight)

        self._summary = QLineEdit()
        self._summary.setPlaceholderText("Task name")
        form.addRow("Summary:", self._summary)

        self._description = QTextEdit()
        self._description.setFixedHeight(80)
        self._description.setPlaceholderText("Optional notes")
        form.addRow("Notes:", self._description)

        self._has_due = QCheckBox("Set due date")
        self._has_due.toggled.connect(lambda c: self._due.setEnabled(c))
        self._due = QDateEdit(QDate.currentDate())
        self._due.setCalendarPopup(True)
        self._due.setEnabled(False)
        due_row = QHBoxLayout()
        due_row.setSpacing(8)
        due_row.addWidget(self._has_due)
        due_row.addWidget(self._due)
        form.addRow("Due:", due_row)

        self._completed = QCheckBox("Mark as completed")
        form.addRow("", self._completed)

        layout.addLayout(form)
        layout.addSpacing(4)

        buttons = QHBoxLayout()
        buttons.setSpacing(8)
        ok_btn = QPushButton("Save" if task else "Add Task")
        ok_btn.clicked.connect(self._accept)
        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(self.reject)
        buttons.addStretch()
        buttons.addWidget(cancel_btn)
        buttons.addWidget(ok_btn)
        layout.addLayout(buttons)

        if task:
            self._summary.setText(task.summary)
            self._description.setPlainText(task.description)
            self._completed.setChecked(task.completed)
            if task.due:
                self._has_due.setChecked(True)
                self._due.setDate(QDate(task.due.year, task.due.month, task.due.day))

    def _accept(self):
        if not self._summary.text().strip():
            self._summary.setFocus()
            return
        self.accept()

    def task(self) -> Task:
        from datetime import datetime
        due = None
        if self._has_due.isChecked():
            d = self._due.date()
            due = datetime(d.year(), d.month(), d.day())
        return Task(
            uid="",
            summary=self._summary.text().strip(),
            description=self._description.toPlainText(),
            due=due,
            completed=self._completed.isChecked(),
        )


class SettingsDialog(QDialog):
    def __init__(self, config: Config, parent=None):
        super().__init__(parent)
        self.config = config
        self.setWindowTitle("Settings")
        self.setMinimumWidth(420)
        self._build_ui()
        self._load()

    def _build_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 20, 24, 20)
        layout.setSpacing(16)

        title = _title_label("Settings")
        layout.addWidget(title)

        sep = QFrame()
        sep.setFrameShape(QFrame.Shape.HLine)
        sep.setFrameShadow(QFrame.Shadow.Sunken)
        layout.addWidget(sep)

        form = QFormLayout()
        form.setSpacing(10)
        form.setLabelAlignment(Qt.AlignmentFlag.AlignRight)

        self._url = QLineEdit()
        self._url.setPlaceholderText("https://nextcloud.example.com")
        form.addRow("Server URL:", self._url)

        self._username = QLineEdit()
        form.addRow("Username:", self._username)

        self._password = QLineEdit()
        self._password.setEchoMode(QLineEdit.EchoMode.Password)
        form.addRow("Password:", self._password)

        self._verify_ssl = QCheckBox("Verify SSL certificate")
        self._verify_ssl.setChecked(True)
        form.addRow("", self._verify_ssl)

        layout.addLayout(form)
        layout.addSpacing(4)

        buttons = QHBoxLayout()
        buttons.setSpacing(8)
        ok_btn = QPushButton("Save")
        ok_btn.clicked.connect(self._save)
        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(self.reject)
        buttons.addStretch()
        buttons.addWidget(cancel_btn)
        buttons.addWidget(ok_btn)
        layout.addLayout(buttons)

    def _load(self):
        self._url.setText(self.config.server_url)
        self._username.setText(self.config.username)
        self._password.setText(self.config.password)
        self._verify_ssl.setChecked(self.config.verify_ssl)

    def _save(self):
        self.config.server_url = self._url.text().strip()
        self.config.username = self._username.text().strip()
        self.config.password = self._password.text()
        self.config.verify_ssl = self._verify_ssl.isChecked()
        self.config.save()
        self.accept()
