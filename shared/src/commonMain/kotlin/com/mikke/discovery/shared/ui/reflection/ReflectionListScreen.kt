package com.mikke.discovery.shared.ui.reflection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikke.discovery.shared.discovery.ReflectionUiModel
import com.mikke.discovery.shared.ui.discovery.DiscoveryColors
import com.mikke.discovery.shared.ui.discovery.DiscoveryRadius
import com.mikke.discovery.shared.ui.discovery.DiscoverySpacing
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val MAX_REFLECTION_LENGTH = 2000

/**
 * ユーザー主導のReflection（日記的振り返り）一覧画面。
 *
 * 過去の振り返り一覧を表示し、FAB（Floating Action Button）から
 * 新規振り返りの入力（本文＋moodピッカー1〜5）を行うことができる。
 *
 * @param reflections 過去の振り返り一覧（新しい順）
 * @param onAddReflection 新規振り返り追加コールバック（content: 本文, mood: 1〜5の評価値）
 * @param onBack 戻るボタン押下時のコールバック
 * @param modifier 修飾子
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReflectionListScreen(
    reflections: List<ReflectionUiModel> = emptyList(),
    onAddReflection: (content: String, mood: Int?) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 新規作成ダイアログの表示状態
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DiscoveryColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "日々の振り返り",
                            color = DiscoveryColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "気づきや想いを自由に書き留めましょう",
                            color = DiscoveryColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る",
                            tint = DiscoveryColors.TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DiscoveryColors.Background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = DiscoveryColors.Accent,
                contentColor = DiscoveryColors.AccentText,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "振り返りを書く"
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (reflections.isEmpty()) {
                // 空状態UI
                EmptyReflectionView(
                    onAddClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // 振り返り一覧
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = DiscoverySpacing.pageHorizontal,
                        vertical = DiscoverySpacing.base
                    ),
                    verticalArrangement = Arrangement.spacedBy(DiscoverySpacing.md)
                ) {
                    items(reflections, key = { it.id }) { reflection ->
                        ReflectionCard(
                            reflection = reflection,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    // 新規追加ダイアログ
    if (showAddDialog) {
        AddReflectionDialog(
            onDismiss = { showAddDialog = false },
            onSubmit = { content, mood ->
                onAddReflection(content, mood)
                showAddDialog = false
            }
        )
    }
}

/**
 * 振り返りアイテムカード。
 */
@Composable
private fun ReflectionCard(
    reflection: ReflectionUiModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(DiscoveryRadius.card))
            .background(DiscoveryColors.Surface)
            .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
            .padding(DiscoverySpacing.cardPadding)
    ) {
        // ヘッダー: 日時とmoodバッジ
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatInstant(reflection.createdAt),
                color = DiscoveryColors.TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            if (reflection.mood != null) {
                MoodBadge(mood = reflection.mood)
            }
        }

        Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

        // 振り返り本文
        Text(
            text = reflection.content,
            color = DiscoveryColors.TextPrimary,
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
    }
}

/**
 * 気分 (mood: 1〜5) を表すバッジ表示。
 */
@Composable
private fun MoodBadge(
    mood: Int,
    modifier: Modifier = Modifier
) {
    val (emoji, label) = when (mood) {
        1 -> "😞" to "落ち込み"
        2 -> "🙁" to "もやもや"
        3 -> "😐" to "ふつう"
        4 -> "🙂" to "前向き"
        5 -> "😆" to "最高"
        else -> "✨" to "$mood"
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(DiscoveryRadius.badge))
            .background(DiscoveryColors.BadgeBackground)
            .padding(horizontal = DiscoverySpacing.sm, vertical = DiscoverySpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 12.sp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label ($mood)",
            color = DiscoveryColors.BadgeText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 空状態の表示。
 */
@Composable
private fun EmptyReflectionView(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = DiscoverySpacing.pageHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "✍️",
            fontSize = 48.sp
        )

        Spacer(modifier = Modifier.height(DiscoverySpacing.base))

        Text(
            text = "まだ振り返りがありません",
            color = DiscoveryColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

        Text(
            text = "右下の「＋」ボタンから、今日やってみたことや気づき、今の気分を自由に書き留めてみましょう。",
            color = DiscoveryColors.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(DiscoverySpacing.xl))

        OutlinedButton(
            onClick = onAddClick,
            shape = RoundedCornerShape(DiscoveryRadius.button),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = DiscoveryColors.Accent
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(DiscoverySpacing.xs))
            Text(text = "最初の振り返りを書く")
        }
    }
}

/**
 * 新規振り返り作成ダイアログ。
 */
@Composable
private fun AddReflectionDialog(
    onDismiss: () -> Unit,
    onSubmit: (content: String, mood: Int?) -> Unit
) {
    var content by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf<Int?>(null) }

    val isLengthValid = content.length <= MAX_REFLECTION_LENGTH
    val canSubmit = content.isNotBlank() && isLengthValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "振り返りを書く",
                color = DiscoveryColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // moodピッカー (1〜5)
                Text(
                    text = "今の気分（任意）",
                    color = DiscoveryColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

                MoodPicker(
                    selectedMood = selectedMood,
                    onMoodSelected = { mood ->
                        // 同じmoodをタップしたら選択解除
                        selectedMood = if (selectedMood == mood) null else mood
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(DiscoverySpacing.base))

                // 本文入力欄
                Text(
                    text = "振り返り・メモ",
                    color = DiscoveryColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = {
                        Text(
                            text = "やってみて気づいたこと、感じたこと、メモなどを自由に書いてみましょう",
                            color = DiscoveryColors.TextTertiary,
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    shape = RoundedCornerShape(DiscoveryRadius.selector),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DiscoveryColors.Accent,
                        unfocusedBorderColor = DiscoveryColors.BorderSubtle
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 文字数カウンター
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "${content.length} / $MAX_REFLECTION_LENGTH 字",
                        color = if (isLengthValid) DiscoveryColors.TextTertiary else DiscoveryColors.ErrorText,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (canSubmit) {
                        onSubmit(content.trim(), selectedMood)
                    }
                },
                enabled = canSubmit,
                shape = RoundedCornerShape(DiscoveryRadius.button),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DiscoveryColors.Accent,
                    contentColor = DiscoveryColors.AccentText
                )
            ) {
                Text("記録する")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "キャンセル",
                    color = DiscoveryColors.TextSecondary
                )
            }
        },
        containerColor = DiscoveryColors.Surface,
        shape = RoundedCornerShape(DiscoveryRadius.card)
    )
}

/**
 * 1〜5のmood選択セレクター。
 */
@Composable
private fun MoodPicker(
    selectedMood: Int?,
    onMoodSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val moods = listOf(
        1 to "😞",
        2 to "🙁",
        3 to "😐",
        4 to "🙂",
        5 to "😆"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        moods.forEach { (score, emoji) ->
            val isSelected = selectedMood == score
            val bgColor = if (isSelected) DiscoveryColors.Accent else DiscoveryColors.SurfaceSecondary
            val borderColor = if (isSelected) DiscoveryColors.Accent else DiscoveryColors.BorderSubtle

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(DiscoveryRadius.selector))
                    .background(bgColor)
                    .border(1.5.dp, borderColor, RoundedCornerShape(DiscoveryRadius.selector))
                    .clickable(
                        role = Role.RadioButton,
                        onClick = { onMoodSelected(score) }
                    )
                    .semantics {
                        role = Role.RadioButton
                        contentDescription = "気分 $score $emoji"
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = emoji, fontSize = 16.sp)
                    Text(
                        text = "$score",
                        fontSize = 9.sp,
                        color = if (isSelected) DiscoveryColors.AccentText else DiscoveryColors.TextTertiary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * KMP共通の日時フォーマット処理（Instant -> YYYY/MM/DD HH:mm）。
 */
@Suppress("DEPRECATION")
internal fun formatInstant(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val month = dt.monthNumber.toString().padStart(2, '0')
    val day = dt.dayOfMonth.toString().padStart(2, '0')
    val hour = dt.hour.toString().padStart(2, '0')
    val minute = dt.minute.toString().padStart(2, '0')
    return "${dt.year}/$month/$day $hour:$minute"
}

/**
 * プレビュー用Composable（ダミーデータ付き）。
 */
@Composable
fun ReflectionListScreenPreview() {
    val sampleReflections = listOf(
        ReflectionUiModel(
            id = "preview-1",
            content = "今日はPythonで新しいアルゴリズムを試してみた。データ構造を工夫すると実行速度が劇的に変わって面白かった！",
            mood = 5,
            createdAt = Instant.fromEpochMilliseconds(1725624000000L)
        ),
        ReflectionUiModel(
            id = "preview-2",
            content = "UIのデザイン方針について少し悩んだ。シンプルさを優先するか多機能にするかのバランスが難しい。",
            mood = 3,
            createdAt = Instant.fromEpochMilliseconds(1725537600000L)
        )
    )

    ReflectionListScreen(
        reflections = sampleReflections,
        onAddReflection = { _, _ -> },
        onBack = {}
    )
}
