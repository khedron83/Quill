"""CalDAV client for Nextcloud Tasks sync."""

from __future__ import annotations

import logging
import uuid
from datetime import datetime, date
from dataclasses import dataclass
from typing import Optional

import caldav
from caldav import Calendar
from icalendar import Calendar as iCal, Todo

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

    def _caldav_url(self) -> str:
        base = self.url.rstrip("/")
        if "/remote.php/dav" not in base and "/remote.php/caldav" not in base:
            return f"{base}/remote.php/dav"
        return base

    def _get_dav(self) -> caldav.DAVClient:
        if not self._dav:
            self._dav = caldav.DAVClient(
                url=self._caldav_url(),
                username=self.username,
                password=self.password,
                ssl_verify_cert=self.verify_ssl,
            )
        return self._dav

    def _get_principal(self) -> caldav.Principal:
        if not self._principal:
            self._principal = self._get_dav().principal()
        return self._principal

    def get_calendars(self) -> list[tuple[str, str]]:
        try:
            principal = self._get_principal()
            calendars = principal.calendars()
            return [
                (cal.name, str(cal.url))
                for cal in calendars
                if "VTODO" in cal.get_supported_components()
            ]
        except Exception as e:
            logger.error(f"Failed to get calendars: {e}")
            return []

    def create_calendar(self, name: str) -> bool:
        try:
            principal = self._get_principal()
            principal.make_calendar(name=name, supported_calendar_component_set=["VTODO"])
            return True
        except Exception as e:
            logger.error(f"Failed to create calendar: {e}")
            return False

    def get_tasks(self, calendar_url: str) -> list[Task]:
        try:
            cal = Calendar(client=self._get_dav(), url=calendar_url)
            todos = cal.todos(include_completed=True)
            tasks = []
            for todo in todos:
                task = self._parse_todo(todo, calendar_url)
                if task and not task.parent_uid:
                    tasks.append(task)
            return tasks
        except Exception as e:
            logger.error(f"Failed to get tasks: {e}")
            return []

    def _parse_todo(self, todo, calendar_name: str) -> Optional[Task]:
        try:
            comp = todo.icalendar_component
            uid = str(comp.get("UID", "")) or str(uuid.uuid4())
            summary = str(comp.get("SUMMARY", ""))
            description = str(comp.get("DESCRIPTION", "")) if comp.get("DESCRIPTION") else ""

            due = None
            if comp.get("DUE"):
                due_val = comp["DUE"].dt
                if isinstance(due_val, datetime):
                    due = due_val
                elif isinstance(due_val, date):
                    due = datetime(due_val.year, due_val.month, due_val.day)

            parent_uid = str(comp.get("RELATED-TO", "")) or None
            completed = str(comp.get("STATUS", "")) == "COMPLETED"

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
        try:
            cal = Calendar(client=self._get_dav(), url=calendar_url)
            ical = iCal()
            todo = Todo()
            todo.add("uid", task.uid or str(uuid.uuid4()))
            todo.add("summary", task.summary)
            if task.description:
                todo.add("description", task.description)
            if task.due:
                todo.add("due", task.due)
            todo.add("status", "COMPLETED" if task.completed else "NEEDS-ACTION")
            if task.parent_uid:
                todo.add("related-to", task.parent_uid)
            ical.add_component(todo)
            cal.add_todo(ical=ical.to_ical().decode("utf-8"))
            return True
        except Exception as e:
            logger.error(f"Failed to create task: {e}")
            return False

    def update_task(self, calendar_url: str, task: Task) -> bool:
        try:
            cal = Calendar(client=self._get_dav(), url=calendar_url)
            obj = cal.object_by_uid(task.uid)
            with obj.edit_icalendar_component() as comp:
                comp["SUMMARY"] = task.summary
                if task.description:
                    comp["DESCRIPTION"] = task.description
                elif "DESCRIPTION" in comp:
                    del comp["DESCRIPTION"]
                if task.due:
                    comp.pop("DUE", None)
                    comp.add("DUE", task.due)
                elif "DUE" in comp:
                    del comp["DUE"]
                comp["STATUS"] = "COMPLETED" if task.completed else "NEEDS-ACTION"
            obj.save()
            return True
        except Exception as e:
            logger.error(f"Failed to update task: {e}")
            return False

    def delete_task(self, calendar_url: str, task_uid: str) -> bool:
        try:
            cal = Calendar(client=self._get_dav(), url=calendar_url)
            cal.object_by_uid(task_uid).delete()
            return True
        except Exception as e:
            logger.error(f"Failed to delete task: {e}")
            return False
