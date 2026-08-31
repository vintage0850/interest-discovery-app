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
import androidx.navigation.toRoute
import com.example.myapplication.shared.TaskRepository
import com.example.myapplication.shared.db.DatabaseDriverFactory
import com.example.myapplication.shared.reversefaq.Question
import com.example.myapplication.shared.reversefaq.QuestionAnswer
import com.example.myapplication.shared.reversefaq.ReverseFaqState
import com.example.myapplication.shared.reversefaq.SqlDelightReverseFaqRepository
import com.example.myapplication.shared.ui.reversefaq.AddCaseScreen
import com.example.myapplication.shared.ui.reversefaq.ContextInputScreen
import com.example.myapplication.shared.ui.reversefaq.HomeScreen
import com.example.myapplication.shared.ui.reversefaq.QuestionDetailScreen
import com.example.myapplication.shared.ui.reversefaq.QuestionListScreen
import com.example.myapplication.shared.ui.theme.SharedAppTheme
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Serializable
private object ReverseFaqHome

@Serializable
private object ReverseFaqAdd

@Serializable
private data class ReverseFaqContext(val caseId: Long)

@Serializable
private data class ReverseFaqQuestions(val caseId: Long)

@Serializable
private data class ReverseFaqQuestionDetail(val questionId: Long)

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
        var isAnalyzing by remember { mutableStateOf(false) }

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

        NavHost(navController = navController, startDestination = ReverseFaqHome) {
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
            composable<ReverseFaqHome> {
                HomeScreen(
                    cases = cases,
                    onAddCase = {
                        navController.navigate(ReverseFaqAdd) { launchSingleTop = true }
                    },
                    onCaseClick = { case ->
                        navController.navigate(ReverseFaqQuestions(case.id)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable<ReverseFaqAdd> {
                AddCaseScreen(
                    onCaseAdded = { title ->
                        reverseFaqState.createCaseAndReturnId(title) { caseId ->
                            navController.navigate(ReverseFaqContext(caseId)) {
                                popUpTo<ReverseFaqAdd> { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable<ReverseFaqContext> { backStackEntry ->
                val args = backStackEntry.toRoute<ReverseFaqContext>()
                val case = cases.find { it.id == args.caseId }
                if (case != null) {
                    ContextInputScreen(
                        caseTitle = case.title,
                        onAnalyze = { documentText, userContextJson ->
                            isAnalyzing = true
                            reverseFaqState.analyzeCase(
                                args.caseId,
                                documentText,
                                userContextJson
                            ) { success ->
                                isAnalyzing = false
                                if (success) {
                                    navController.navigate(ReverseFaqQuestions(args.caseId)) {
                                        popUpTo<ReverseFaqContext> { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        },
                        onBack = { navController.popBackStack() },
                        isLoading = isAnalyzing
                    )
                }
            }
            composable<ReverseFaqQuestions> { backStackEntry ->
                val args = backStackEntry.toRoute<ReverseFaqQuestions>()
                val questions by reverseFaqState.questionsForCase(args.caseId).collectAsState()
                val progress by reverseFaqState.progressForCase(args.caseId).collectAsState()
                val case = cases.find { it.id == args.caseId }
                if (case != null) {
                    QuestionListScreen(
                        caseTitle = case.title,
                        questions = questions,
                        progress = progress,
                        onBack = { navController.popBackStack() },
                        onQuestionClick = { question ->
                            navController.navigate(ReverseFaqQuestionDetail(question.id)) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
            composable<ReverseFaqQuestionDetail> { backStackEntry ->
                val args = backStackEntry.toRoute<ReverseFaqQuestionDetail>()
                val questionId = args.questionId
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

@OptIn(ExperimentalTime::class)
private fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
