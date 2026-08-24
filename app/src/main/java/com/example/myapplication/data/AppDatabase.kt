package com.example.myapplication.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: カレンダー連携用の calendarEventId 列と、サブタスクのテーブルを追加する。
 * CREATE 文は Room が生成するスキーマと一致させる必要がある（起動時に検証されるため）。
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `calendarEventId` INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `subtasks` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`taskId` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`isCompleted` INTEGER NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtasks_taskId` ON `subtasks` (`taskId`)")
    }
}

/**
 * v2 -> v3: 固定 enum だったカテゴリ（TaskList）を categories テーブルへ移す。
 *
 * tasks.listType（TEXT）を捨てて categories への外部キー categoryId に置き換えるため、
 * SQLite では列の付け替えができずテーブルを作り直して詰め替える。
 * 既存タスクの分類が消えないよう、旧 enum 名を新しいカテゴリの id へ対応させる。
 */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL )"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)"
        )

        // 旧 enum と同じ並びで id 1..3 を割り当て、下の CASE 式から参照する
        Category.DEFAULTS.forEachIndexed { index, name ->
            db.execSQL(
                "INSERT INTO `categories` (`id`, `name`, `sortOrder`) VALUES (?, ?, ?)",
                arrayOf<Any>(index + 1, name, index)
            )
        }

        db.execSQL(
            "CREATE TABLE `tasks_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`deadline` INTEGER NOT NULL, " +
                "`importance` INTEGER NOT NULL, " +
                "`urgency` INTEGER NOT NULL, " +
                "`categoryId` INTEGER, " +
                "`isCompleted` INTEGER NOT NULL, " +
                "`progress` INTEGER NOT NULL, " +
                "`notificationTime` INTEGER, " +
                "`calendarEventId` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL(
            "INSERT INTO `tasks_new` (" +
                "`id`, `title`, `deadline`, `importance`, `urgency`, `categoryId`, " +
                "`isCompleted`, `progress`, `notificationTime`, `calendarEventId`, `createdAt`) " +
                "SELECT `id`, `title`, `deadline`, `importance`, `urgency`, " +
                "CASE `listType` " +
                "WHEN 'SKILL' THEN 1 WHEN 'SUBMISSION' THEN 2 WHEN 'WANT' THEN 3 " +
                "ELSE NULL END, " +
                "`isCompleted`, `progress`, `notificationTime`, `calendarEventId`, `createdAt` " +
                "FROM `tasks`"
        )
        // subtasks の外部キーは名前で tasks を指しているので、付け替え後もそのまま繋がる
        db.execSQL("DROP TABLE `tasks`")
        db.execSQL("ALTER TABLE `tasks_new` RENAME TO `tasks`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_categoryId` ON `tasks` (`categoryId`)")
    }
}

/**
 * v3 -> v4: tasks.calendarEventId を INTEGER から TEXT へ変える。
 *
 * カレンダー連携が CalendarProvider から Google Calendar REST API に置き換わり、
 * 予定の識別子が「端末ローカルの数値 ID」から「API のイベント ID（文字列）」になったため。
 *
 * **旧 calendarEventId は REST API のイベント ID と互換性が無いので、移行時は一律 NULL にする。**
 * 端末のカレンダーに残っている予定はアプリからは辿れなくなるため、
 * 連携していたタスクはユーザーに再度連携し直してもらう前提とする。
 *
 * SQLite は列の型変更ができないので、MIGRATION_2_3 と同じくテーブルを作り直して詰め替える。
 * subtasks は名前で `tasks` を参照しているため、DROP -> RENAME の後もそのまま繋がる。
 * なお Room が `PRAGMA foreign_keys = ON` を実行するのは onOpen（＝移行が全部終わった後）なので、
 * DROP TABLE の暗黙 DELETE が subtasks へ CASCADE することはない。
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `tasks_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`deadline` INTEGER NOT NULL, " +
                "`importance` INTEGER NOT NULL, " +
                "`urgency` INTEGER NOT NULL, " +
                "`categoryId` INTEGER, " +
                "`isCompleted` INTEGER NOT NULL, " +
                "`progress` INTEGER NOT NULL, " +
                "`notificationTime` INTEGER, " +
                "`calendarEventId` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL(
            "INSERT INTO `tasks_new` (" +
                "`id`, `title`, `deadline`, `importance`, `urgency`, `categoryId`, " +
                "`isCompleted`, `progress`, `notificationTime`, `calendarEventId`, `createdAt`) " +
                "SELECT `id`, `title`, `deadline`, `importance`, `urgency`, `categoryId`, " +
                // 旧 ID は使えないので捨てる（＝全タスクが未連携の状態に戻る）
                "`isCompleted`, `progress`, `notificationTime`, NULL, `createdAt` " +
                "FROM `tasks`"
        )
        db.execSQL("DROP TABLE `tasks`")
        db.execSQL("ALTER TABLE `tasks_new` RENAME TO `tasks`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_categoryId` ON `tasks` (`categoryId`)")
    }
}

/**
 * v4 -> v5: 「空き時間検知による『始めさせる』通知」機能のためのテーブル追加。
 *
 * - tasks.status（TEXT, デフォルト 'TODO'）: Room の enum ネイティブサポートにより TEXT 列として
 *   マッピングされる（TypeConverter 不要）。既存行はすべて未着手（TODO）として扱う
 * - notified_slots テーブル: 同じ空き時間帯に何度も通知しないための重複防止レコード
 */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'TODO'")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notified_slots` (" +
                "`startMillis` INTEGER PRIMARY KEY NOT NULL, " +
                "`endMillis` INTEGER NOT NULL )"
        )
    }
}

/** 新規インストール時は移行が走らないので、初期カテゴリはここで入れる。 */
private val SEED_CALLBACK = object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        Category.DEFAULTS.forEachIndexed { index, name ->
            db.execSQL(
                "INSERT INTO `categories` (`name`, `sortOrder`) VALUES (?, ?)",
                arrayOf<Any>(name, index)
            )
        }
    }
}

@Database(
    entities = [Task::class, SubTask::class, Category::class, NotifiedSlot::class],
    version = 5,
    // スキーマ JSON を app/schemas/ に書き出す。
    // マイグレーションテスト（MigrationTest）が各バージョン間の検証に使うので必須。
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "task_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .addCallback(SEED_CALLBACK)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
