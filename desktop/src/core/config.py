"""Persistent app configuration."""

import json
import sys
from pathlib import Path
from typing import Any


def _config_dir() -> Path:
    if sys.platform == "win32":
        base = Path.home() / "AppData" / "Local"
    else:
        base = Path.home() / ".config"
    return base / "quill"


def _config_path() -> Path:
    return _config_dir() / "config.json"


_DEFAULTS: dict[str, Any] = {
    "server_url": "",
    "username": "",
    "password": "",
    "verify_ssl": True,
    "theme": "dark",
}


class Config:
    def __init__(self):
        self._data: dict[str, Any] = dict(_DEFAULTS)
        self._load()

    def _load(self):
        path = _config_path()
        if path.exists():
            try:
                self._data.update(json.loads(path.read_text()))
            except (json.JSONDecodeError, OSError):
                pass

    def save(self):
        path = _config_path()
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(self._data, indent=2))

    def get(self, key: str, default: Any = None) -> Any:
        return self._data.get(key, default)

    def set(self, key: str, value: Any):
        self._data[key] = value

    @property
    def server_url(self) -> str:
        return self._data.get("server_url", "")

    @server_url.setter
    def server_url(self, v: str):
        self._data["server_url"] = v

    @property
    def username(self) -> str:
        return self._data.get("username", "")

    @username.setter
    def username(self, v: str):
        self._data["username"] = v

    @property
    def password(self) -> str:
        return self._data.get("password", "")

    @password.setter
    def password(self, v: str):
        self._data["password"] = v

    @property
    def verify_ssl(self) -> bool:
        return bool(self._data.get("verify_ssl", True))

    @verify_ssl.setter
    def verify_ssl(self, v: bool):
        self._data["verify_ssl"] = v

    @property
    def theme(self) -> str:
        return self._data.get("theme", "dark")

    @theme.setter
    def theme(self, v: str):
        self._data["theme"] = v
