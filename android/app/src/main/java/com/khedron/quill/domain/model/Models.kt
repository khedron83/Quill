package com.khedron.quill.domain.model

import java.time.LocalDate

data class QuillTask(
    val uid: String,
    val summary: String,
    val description: String = "",
    val dueDate: LocalDate? = null,
    val completed: Boolean = false,
    val href: String = "",
)

data class TaskCalendar(
    val name: String,
    val url: String,
)
