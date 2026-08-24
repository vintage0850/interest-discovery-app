package com.example.myapplication.shared.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.shared.TaskRepository
import com.example.myapplication.shared.db.DatabaseDriverFactory
import com.example.myapplication.shared.ui.theme.SharedAppTheme

private const val ROUTE_LIST = "list"
private const val ROUTE_ADD = "add"
private const val ROUTE_CATEGORIES = "categories"

/**
 * Compose Multiplatformアプリのルート。Android/iOS双方のホストから呼ばれる。
 * Android版 `MainActivity` の `NavHost` 配線から、通知権限リクエスト（Android専用）と
 * カレンダー連携関連のパラメータを除いた版。
 */
@Composable
fun App(driverFactory: DatabaseDriverFactory) {
    SharedAppTheme {
        val scope = rememberCoroutineScope()
        val appState = remember(driverFactory) {
            AppState(TaskRepository(driverFactory), scope)
        }

        val navController = rememberNavController()
        val tasks by appState.allTasks.collectAsState()
        val categories by appState.categories.collectAsState()

        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(Unit) {
            appState.messages.collect { message ->
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message)
            }
        }

        NavHost(navController = navController, startDestination = ROUTE_LIST) {
            composable(ROUTE_LIST) {
                TaskListScreen(
                    tasks = tasks,
                    categories = categories,
                    snackbarHostState = snackbarHostState,
                    onAddTask = {
                        // 連打で "add" が積み重なるのを防ぐ
                        navController.navigate(ROUTE_ADD) { launchSingleTop = true }
                    },
                    onManageCategories = {
                        navController.navigate(ROUTE_CATEGORIES) { launchSingleTop = true }
                    },
                    onTaskToggle = appState::toggleCompleted,
                    onSubTaskToggle = appState::toggleSubTaskCompleted,
                    onTaskDelete = appState::deleteTask,
                    onUndoDelete = appState::undoDelete,
                    onTaskRename = appState::renameTask
                )
            }
            composable(ROUTE_ADD) {
                AddTaskScreen(
                    categories = categories,
                    onAddCategory = appState::addCategory,
                    onTaskAdded = { input: NewTaskInput ->
                        appState.addTask(
                            title = input.title,
                            deadline = input.deadline,
                            importance = input.importance,
                            urgency = input.urgency,
                            categoryId = input.categoryId,
                            subTaskTitles = input.subTaskTitles
                        )
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(ROUTE_CATEGORIES) {
                CategoryManagerScreen(
                    categories = categories,
                    snackbarHostState = snackbarHostState,
                    onAdd = appState::addCategory,
                    onRename = appState::renameCategory,
                    onDelete = appState::deleteCategory,
                    countTasksIn = appState::countTasksInCategory,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
