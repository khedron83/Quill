package com.khedron.quill.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.khedron.quill.ui.settings.SettingsScreen
import com.khedron.quill.ui.tasks.TasksScreen

@Composable
fun NavGraph() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "tasks") {
        composable("tasks") {
            TasksScreen(onOpenSettings = { nav.navigate("settings") })
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
