"""Main application window."""

from PySide6.QtWidgets import (
    QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QListWidget, QListWidgetItem, QPushButton, QMessageBox,
    QInputDialog, QDialog, QLabel, QLineEdit, QCheckBox,
    QFormLayout, QMenuBar, QMenu, QStatusBar,
)
from PySide6.QtCore import Qt
from PySide6.QtGui import QAction, QIcon
from src.core.config import Config
from src.core.caldav_client import CalDAVClient


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.config = Config()
        self.caldav_client: CalDAVClient | None = None
        self.setWindowTitle("Quill")
        self.setMinimumSize(800, 600)
        self._setup_ui()
        self._init()

    def _setup_ui(self):
        # Menu bar
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

        # Central widget
        central = QWidget()
        self.setCentralWidget(central)
        layout = QHBoxLayout(central)

        # Calendar list (left)
        self._calendar_list = QListWidget()
        self._calendar_list.itemClicked.connect(self._on_calendar_selected)
        layout.addWidget(QLabel("Calendars:"))

        # Task list (right)
        self._task_list = QListWidget()
        layout.addWidget(self._task_list, 1)

        # Status bar
        self.setStatusBar(QStatusBar())

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
        url = item.data(Qt.UserRole)
        self._load_tasks(url)

    def _load_tasks(self, calendar_url: str):
        if not self.caldav_client:
            return
        tasks = self.caldav_client.get_tasks(calendar_url)
        self._task_list.clear()
        for task in tasks:
            item_text = f"{'✓' if task.completed else '○'} {task.summary}"
            self._task_list.addItem(item_text)

    def _open_settings(self):
        dlg = SettingsDialog(self.config, self)
        if dlg.exec():
            self._connect_caldav()
            self._load_calendars()

    def _show_about(self):
        QMessageBox.about(
            self,
            "About Quill",
            "<b>Quill</b> v1.0<br><br>"
            "A task manager with Nextcloud CalDAV sync.<br>"
            "Organize tasks into calendars with subtasks support.<br><br>"
            "Licensed under the GNU General Public License v3.0.",
        )


class SettingsDialog(QDialog):
    def __init__(self, config: Config, parent=None):
        super().__init__(parent)
        self.config = config
        self.setWindowTitle("Settings")
        self.setMinimumWidth(400)
        self._build_ui()
        self._load()

    def _build_ui(self):
        layout = QVBoxLayout(self)
        form = QFormLayout()

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

        buttons = QHBoxLayout()
        ok_btn = QPushButton("OK")
        ok_btn.clicked.connect(self._save)
        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(self.reject)
        buttons.addStretch()
        buttons.addWidget(ok_btn)
        buttons.addWidget(cancel_btn)
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
