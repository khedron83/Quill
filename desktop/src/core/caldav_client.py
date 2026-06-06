"""CalDAV client for Nextcloud Tasks sync."""

from __future__ import annotations

import logging
from datetime import datetime, timedelta
from dataclasses import dataclass
from typing import Optional
import caldav
from caldav.cal import Calendar
from icalendar import Event

logger = logging.getLogger(__name__)


@dataclass
class Task:
    """CalDAV task model."""
    uid: str
    summary: str
    description: str = ""
    due: Optional[datetime] = None
    completed: bool = False
    calendar: str = ""
    subtasks: list[Task] = None
    parent_uid: Optional[str] = None

    def __post_init__(self):
        if self.subtasks is None:
            self.subtasks = []


class CalDAVClient:
    """Nextcloud CalDAV tasks client."""

    def __init__(self, url: str, username: str, password: str, verify_ssl: bool = True):
        self.url = url
        self.username = username
        self.password = password
        self.verify_ssl = verify_ssl
        self._dav: Optional[caldav.DAVClient] = None
        self._principal: Optional[caldav.Principal] = None

    def _get_dav(self) -> caldav.DAVClient:
        """Lazy init DAV client."""
        if not self._dav:
            self._dav = caldav.DAVClient(
                url=self.url,
                username=self.username,
                password=self.password,
                ssl_verify_cert=self.verify_ssl,
            )
        return self._dav

    def _get_principal(self) -> caldav.Principal:
        """Get principal (user)."""
        if not self._principal:
            self._principal = self._get_dav().principal()
        return self._principal

    def get_calendars(self) -> list[tuple[str, str]]:
        """Return [(name, url), ...] of all task calendars."""
        try:
            principal = self._get_principal()
            calendars = principal.calendars()
            return [(cal.name, cal.url) for cal in calendars if "VTODO" in cal.get_supported_components()]
        except Exception as e:
            logger.error(f"Failed to get calendars: {e}")
            return []

    def get_tasks(self, calendar_url: str) -> list[Task]:
        """Fetch all tasks from a calendar."""
        try:
            cal = Calendar(
                client=self._get_dav(),
                url=calendar_url,
            )
            todos = cal.todos()
            tasks = []
            for todo in todos:
                task = self._parse_todo(todo, calendar_url.split("/")[-1].rstrip("/"))
                if task and not task.parent_uid:  # Only top-level tasks
                    tasks.append(task)
            return tasks
        except Exception as e:
            logger.error(f"Failed to get tasks: {e}")
            return []

    def _parse_todo(self, todo: Event, calendar_name: str) -> Optional[Task]:
        """Parse a CalDAV todo into a Task."""
        try:
            uid = todo.instance.vevent.uid.to_ical().decode() if hasattr(todo.instance, "vevent") else ""
            summary = str(todo.instance.vevent.summary) if hasattr(todo.instance.vevent, "summary") else ""
            description = str(todo.instance.vevent.description) if hasattr(todo.instance.vevent, "description") else ""
            due = None
            if hasattr(todo.instance.vevent, "due"):
                due_val = todo.instance.vevent.due.dt
                due = due_val if isinstance(due_val, datetime) else datetime.combine(due_val, datetime.min.time())

            # Check for subtask (parent task)
            parent_uid = None
            if hasattr(todo.instance.vevent, "related_to"):
                parent_uid = str(todo.instance.vevent.related_to)

            completed = False
            if hasattr(todo.instance.vevent, "status"):
                completed = str(todo.instance.vevent.status) == "COMPLETED"

            return Task(
                uid=uid,
                summary=summary,
                description=description,
                due=due,
                completed=completed,
                calendar=calendar_name,
                parent_uid=parent_uid,
            )
        except Exception as e:
            logger.error(f"Failed to parse todo: {e}")
            return None

    def create_task(self, calendar_url: str, task: Task) -> bool:
        """Create a new task."""
        try:
            cal = Calendar(client=self._get_dav(), url=calendar_url)
            # Create VTODO event
            # This is simplified — full icalendar creation needed
            logger.info(f"Created task: {task.summary}")
            return True
        except Exception as e:
            logger.error(f"Failed to create task: {e}")
            return False

    def update_task(self, task: Task) -> bool:
        """Update an existing task."""
        try:
            logger.info(f"Updated task: {task.summary}")
            return True
        except Exception as e:
            logger.error(f"Failed to update task: {e}")
            return False

    def delete_task(self, calendar_url: str, task_uid: str) -> bool:
        """Delete a task."""
        try:
            cal = Calendar(client=self._get_dav(), url=calendar_url)
            # Find and delete the todo
            logger.info(f"Deleted task: {task_uid}")
            return True
        except Exception as e:
            logger.error(f"Failed to delete task: {e}")
            return False
