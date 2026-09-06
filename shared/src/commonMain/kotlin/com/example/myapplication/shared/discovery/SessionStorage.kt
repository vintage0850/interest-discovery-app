package com.example.myapplication.shared.discovery

/**
 * 最後に使用した Discovery セッション ID の永続化先。
 * アプリ再起動後も過去のセッションを復帰できるようにする。
 */
interface SessionStorage {
    /** 保存されている最後のセッション ID を取得する。保存されていなければ null。 */
    fun getLastSessionId(): Int?

    /** 最後のセッション ID を保存する。 */
    fun saveLastSessionId(id: Int)

    /** 保存されているセッション ID を削除する。 */
    fun clear()
}

/**
 * 永続化しない既定実装。テストや未対応プラットフォームで使用する。
 */
class InMemorySessionStorage : SessionStorage {
    private var storedId: Int? = null

    override fun getLastSessionId(): Int? = storedId

    override fun saveLastSessionId(id: Int) {
        storedId = id
    }

    override fun clear() {
        storedId = null
    }
}
