# Quill

A task manager with **Nextcloud CalDAV** sync for both desktop and Android.

Organize your tasks into calendars with support for subtasks. Everything syncs back to your Nextcloud instance, so your tasks are always up-to-date across all devices.

## Features

- **CalDAV Sync** — All tasks sync with Nextcloud Tasks (and any other CalDAV server)
- **Calendars** — Organize tasks into different calendars (work, personal, etc.)
- **Subtasks** — Break down tasks into smaller subtasks
- **Cross-platform** — Desktop (Linux/macOS/Windows) and Android apps included
- **Offline Support** — Tasks work offline and sync when reconnected

## Requirements

### Desktop (Python)
- Python 3.11+
- PySide6
- caldav

### Android
- Android 8.0+
- Jetpack Compose

## Install

### Desktop

```bash
git clone https://github.com/khedron83/Quill.git
cd Quill
python -m venv .venv
source .venv/bin/activate     # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python desktop/main.py
```

### Android

Clone and open `android/` in Android Studio, then build and run.

## First Run

Configure your Nextcloud server:
- **Server URL**: `https://your-nextcloud.example.com`
- **Username**: Your Nextcloud username
- **Password**: Your Nextcloud password (or app password)

On first connection, Quill will fetch your existing task calendars and sync.

## How It Works

**Calendars** map to CalDAV calendar collections in your Nextcloud Tasks app. Each calendar can contain multiple tasks.

**Subtasks** are implemented using CalDAV's `RELATED-TO` field, so they sync seamlessly with any CalDAV client.

## License

Licensed under the **GNU General Public License v3.0**. See [LICENSE](LICENSE) for details.
