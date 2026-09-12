package com.mikke.discovery.shared.ui.survey

import com.mikke.discovery.shared.discovery.PsychAxis

/**
 * 心理4軸アンケートの各質問データ。
 *
 * @property id 質問のユニーク識別子（1〜8）
 * @property axis 対応する心理軸（INVESTIGATE / CREATE / EXECUTE / COMMUNICATE）
 * @property text 質問文
 */
data class PsychAxisQuestion(
    val id: Int,
    val axis: PsychAxis,
    val text: String
)

/**
 * 心理4軸アンケート（計8問、各軸2問の5件法）。
 *
 * 軸の定義（TASK.md 案件18）:
 * - [PsychAxis.INVESTIGATE]（探究）: 物事の仕組みや原因を深く調べる・分析する関心
 * - [PsychAxis.CREATE]（創造）: 新しいアイデアを形にする・工夫する関心
 * - [PsychAxis.EXECUTE]（実行）: 計画を立てて整理し、着実にやり遂げる関心
 * - [PsychAxis.COMMUNICATE]（伝達）: 学んだことや考えを人に伝え、共有する関心
 */
val PSYCH_AXIS_QUESTIONS: List<PsychAxisQuestion> = listOf(
    // 1. 探究 (INVESTIGATE) - 問1
    PsychAxisQuestion(
        id = 1,
        axis = PsychAxis.INVESTIGATE,
        text = "物事の仕組みや原因を深く調べたり解明したりすることが好きだ"
    ),
    // 2. 創造 (CREATE) - 問1
    PsychAxisQuestion(
        id = 2,
        axis = PsychAxis.CREATE,
        text = "新しいアイデアを形にしたり、オリジナルのものを作ることが楽しい"
    ),
    // 3. 実行 (EXECUTE) - 問1
    PsychAxisQuestion(
        id = 3,
        axis = PsychAxis.EXECUTE,
        text = "計画や段取りを立てて、物事を着実に効率よく進めるのが得意だ"
    ),
    // 4. 伝達 (COMMUNICATE) - 問1
    PsychAxisQuestion(
        id = 4,
        axis = PsychAxis.COMMUNICATE,
        text = "自分が学んだことや面白いと思ったことを、人に分かりやすく伝えるのが好きだ"
    ),
    // 5. 探究 (INVESTIGATE) - 問2
    PsychAxisQuestion(
        id = 5,
        axis = PsychAxis.INVESTIGATE,
        text = "疑問に感じたことは、納得がいくまでデータや事実を確かめたくなる"
    ),
    // 6. 創造 (CREATE) - 問2
    PsychAxisQuestion(
        id = 6,
        axis = PsychAxis.CREATE,
        text = "すでにあるやり方に自分ならではの工夫やアレンジを付け加えるのが好きだ"
    ),
    // 7. 実行 (EXECUTE) - 問2
    PsychAxisQuestion(
        id = 7,
        axis = PsychAxis.EXECUTE,
        text = "ごちゃごちゃした状況を整理整頓したり、手順の無駄をなくすことに達成感がある"
    ),
    // 8. 伝達 (COMMUNICATE) - 問2
    PsychAxisQuestion(
        id = 8,
        axis = PsychAxis.COMMUNICATE,
        text = "人と対話しながらお互いの考えを深め合ったり共有することが楽しい"
    )
)

/**
 * 8問の回答マップ (questionId -> 1..5) から、4軸それぞれの平均スコア (1.0〜5.0) を計算する。
 *
 * @param ratings questionId (1..8) をキー、評価 (1..5) を値とするマップ
 * @return 4軸それぞれの平均スコア（未回答の軸は 0f）
 */
fun calculatePsychAxisScores(ratings: Map<Int, Int>): Map<PsychAxis, Float> {
    val axisBuckets = mutableMapOf<PsychAxis, MutableList<Int>>()
    PsychAxis.entries.forEach { axisBuckets[it] = mutableListOf() }

    for (question in PSYCH_AXIS_QUESTIONS) {
        val rating = ratings[question.id]
        if (rating != null) {
            axisBuckets[question.axis]?.add(rating)
        }
    }

    return axisBuckets.mapValues { (_, values) ->
        if (values.isEmpty()) 0f else values.average().toFloat()
    }
}
