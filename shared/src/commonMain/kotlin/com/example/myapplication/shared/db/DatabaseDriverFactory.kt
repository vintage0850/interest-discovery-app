package com.example.myapplication.shared.db

import app.cash.sqldelight.db.SqlDriver

/**
 * プラットフォームごとのSQLiteドライバ生成を隠蔽する。
 * Android実装は Context を要求しシグネチャが揃わないため、expect/actual ではなく
 * インターフェース + プラットフォーム別実装クラスの方式を取る。
 */
interface DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
