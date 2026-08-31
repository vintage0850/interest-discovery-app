package com.example.myapplication.shared.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.shared.TaskRepository
import com.example.myapplication.shared.db.DatabaseDriverFactory
import com.example.myapplication.shared.reversefaq.Question
import com.example.myapplication.shared.reversefaq.QuestionAnswer
import com.example.myapplication.shared.reversefaq.ReverseFaqState
import com.example.myapplication.shared.reversefaq.SqlDelightReverseFaqRepository
import com.example.myapplication.shared.ui.reversefaq.AddCaseScreen
import com.example.myapplication.shared.ui.reversefaq.HomeScreen
import com.example.myapplication.shared.ui.reversefaq.QuestionDetailScreen
import com.example.myapplication.shared.ui.reversefaq.QuestionListScreen
import com.example.myapplication.shared.ui.theme.SharedAppTheme
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val ROUTE_REVERSE_FAQ_HOME = "reverse_faq_home"
private const val ROUTE_REVERSE_FAQ_ADD = "reverse_faq_add"
private const val ROUTE_REVERSE_FAQ_QUESTIONS = "reverse_faq_questions/{caseId}"
private const val ROUTE_REVERSE_FAQ_QUESTION_DETAIL = "reverse_faq_question/{questionId}"

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
        val reverseFaqState = remember(driverFactory) {
            ReverseFaqState(SqlDelightReverseFaqRepository(driverFactory), scope)
        }

        val navController = rememberNavController()
        val tasks by appState.allTasks.collectAsState()
        val categories by appState.categories.collectAsState()
        val cases by reverseFaqState.allCases.collectAsState()

        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(Unit) {
            appState.messages.collect { message ->
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message)
            }
        }
        LaunchedEffect(Unit) {
            reverseFaqState.messages.collect { message ->
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message)
            }
        }

        NavHost(navController = navController, startDestination = ROUTE_REVERSE_FAQ_HOME) {
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

            // Reverse FAQ（Phase 1）
            composable(ROUTE_REVERSE_FAQ_HOME) {
                HomeScreen(
                    cases = cases,
                    onAddCase = {
                        navController.navigate(ROUTE_REVERSE_FAQ_ADD) { launchSingleTop = true }
                    },
                    onCaseClick = { case ->
                        navController.navigate("reverse_faq_questions/${case.id}") {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(ROUTE_REVERSE_FAQ_ADD) {
                AddCaseScreen(
                    onCaseAdded = { title ->
                        reverseFaqState.createCaseAndGenerateQuestions(title)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(ROUTE_REVERSE_FAQ_QUESTIONS) { backStackEntry ->
                val caseId = backStackEntry.arguments?.getString("caseId")?.toLongOrNull()
                if (caseId != null) {
                    val questions by reverseFaqState.questionsForCase(caseId).collectAsState()
                    val progress by reverseFaqState.progressForCase(caseId).collectAsState()
                    val case = cases.find { it.id == caseId }
                    if (case != null) {
                        QuestionListScreen(
                            caseTitle = case.title,
                            questions = questions,
                            progress = progress,
                            onBack = { navController.popBackStack() },
                            onQuestionClick = { question ->
                                navController.navigate("reverse_faq_question/${question.id}") {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }
            }
            composable(ROUTE_REVERSE_FAQ_QUESTION_DETAIL) { backStackEntry ->
                val questionId = backStackEntry.arguments?.getString("questionId")?.toLongOrNull()
                if (questionId != null) {
                    var question by remember(questionId) { mutableStateOf<Question?>(null) }
                    var answer by remember(questionId) { mutableStateOf<QuestionAnswer?>(null) }
                    LaunchedEffect(questionId) {
                        question = reverseFaqState.getQuestionById(questionId)
                        answer = reverseFaqState.getAnswerForQuestion(questionId)
                    }
                    question?.let { q ->
                        QuestionDetailScreen(
                            question = q,
                            answer = answer,
                            onBack = { navController.popBackStack() },
                            onConfirm = {
                                reverseFaqState.confirmQuestion(questionId)
                                navController.popBackStack()
                            },
                            onSaveAnswer = { text, by ->
                                reverseFaqState.saveAnswer(
                                    questionId,
                                    text,
                                    by,
                                    currentTimeMillis()
                                )
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
private fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
