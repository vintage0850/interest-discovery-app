package com.mikke.discovery.shared.discovery

/**
 * 設定画面に表示する Google アカウント連携状態。
 *
 * Android ホスト側の [GoogleAccountManager] がCredential Managerで取得した結果を、
 * この sealed interface として shared モジュールへ伝播する。
 * shared は UI 表示用の状態だけを持ち、サインイン処理自体は Android ホスト側に委譲する。
 */
sealed interface GoogleAccountState {
    /** BuildConfig に OAuth クライアント ID が設定されていない（機能自体を隠す想定）。 */
    data object NotConfigured : GoogleAccountState

    /** 未連携。サインインフローの開始が可能。 */
    data object NotLinked : GoogleAccountState

    /**
     * 連携済み。
     *
     * @param displayName Google アカウントの表示名（取得できなければ null）
     * @param email Google アカウントのメールアドレス（取得できなければ null）
     * @param photoUrl プロフィール画像 URL（取得できなければ null）
     */
    data class Linked(
        val displayName: String?,
        val email: String?,
        val photoUrl: String?
    ) : GoogleAccountState

    /**
     * 連携に失敗した。
     *
     * 永続化せず、画面にエラーを通知するために使う一時的な状態。
     * 再試行・キャンセル・解除で [NotLinked] または [Linked] に戻る。
     */
    data class LinkFailed(val message: String) : GoogleAccountState
}
