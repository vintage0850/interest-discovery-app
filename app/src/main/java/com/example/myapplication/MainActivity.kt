package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.ui.theme.MyApplicationTheme

private const val ROUTE_LIST = "list"
private const val ROUTE_ADD = "add"
private const val ROUTE_CATEGORIES = "categories"

class MainActivity : ComponentActivity() {
    private val viewModel: TaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                // バックグラウンドでは収集を止める（StateFlow の WhileSubscribed と揃える）
                val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
                val categories by viewModel.categories.collectAsStateWithLifecycle()
                // カレンダー連携の状態。認可状態の初期取得は ViewModel の init で行う
                val authState by viewModel.authState.collectAsStateWithLifecycle()

                // カテゴリ追加時の重複警告などは、どの画面にいても同じ場所に出す
                val snackbarHostState = remember { SnackbarHostState() }
                LaunchedEffect(Unit) {
                    viewModel.messages.collect { message ->
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
                                navController.navigate(ROUTE_CATEGORIES) {
                                    launchSingleTop = true
                                }
                            },
                            onTaskToggle = viewModel::toggleCompleted,
                            onSubTaskToggle = viewModel::toggleSubTaskCompleted,
                            onTaskDelete = viewModel::deleteTask,
                            onUndoDelete = viewModel::undoDelete,
                            authState = authState,
                            onCalendarLinkChange = viewModel::setCalendarLinked,
                            onSignOut = viewModel::signOut
                        )
                    }
                    composable(ROUTE_ADD) {
                        AddTaskScreen(
                            categories = categories,
                            authState = authState,
                            onAddCategory = viewModel::addCategory,
                            onTaskAdded = { input: NewTaskInput ->
                                viewModel.addTask(
                                    title = input.title,
                                    deadline = input.deadline,
                                    importance = input.importance,
                                    urgency = input.urgency,
                                    categoryId = input.categoryId,
                                    subTaskTitles = input.subTaskTitles,
                                    addToCalendar = input.addToCalendar
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
                            onAdd = viewModel::addCategory,
                            onRename = viewModel::renameCategory,
                            onDelete = viewModel::deleteCategory,
                            countTasksIn = viewModel::countTasksInCategory,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
