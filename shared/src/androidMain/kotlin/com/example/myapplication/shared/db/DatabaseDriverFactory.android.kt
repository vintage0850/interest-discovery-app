package com.example.myapplication.shared.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

class AndroidDatabaseDriverFactory(private val context: Context) : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver =
        AndroidSqliteDriver(
            schema = SharedDatabase.Schema,
            context = context,
            name = "shared.db",
            callback = object : AndroidSqliteDriver.Callback(SharedDatabase.Schema) {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // ON DELETE CASCADE / SET NULL を機能させるために必須
                    db.execSQL("PRAGMA foreign_keys=ON;")
                }
            }
        )
}
