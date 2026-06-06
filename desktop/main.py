"""Quill task manager — entry point."""

import sys
from pathlib import Path
from PySide6.QtWidgets import QApplication
from PySide6.QtGui import QIcon
from src.ui.main_window import MainWindow

_ICON = Path(__file__).parent / "src" / "resources" / "icon.svg"


def main():
    app = QApplication(sys.argv)
    app.setApplicationName("Quill")
    app.setApplicationVersion("1.0.0")
    app.setOrganizationName("quill")
    if _ICON.exists():
        app.setWindowIcon(QIcon(str(_ICON)))

    window = MainWindow()
    window.show()

    sys.exit(app.exec())


if __name__ == "__main__":
    main()
