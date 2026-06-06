"""Quill task manager — entry point."""

import sys
from PySide6.QtWidgets import QApplication
from src.ui.main_window import MainWindow


def main():
    app = QApplication(sys.argv)
    app.setApplicationName("Quill")
    app.setApplicationVersion("1.0.0")
    app.setOrganizationName("quill")

    window = MainWindow()
    window.show()

    sys.exit(app.exec())


if __name__ == "__main__":
    main()
