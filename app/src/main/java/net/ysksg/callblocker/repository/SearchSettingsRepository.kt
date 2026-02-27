package net.ysksg.callblocker.repository

import android.content.Context
import android.util.Log

/**
 * Web検索エンジン設定を管理するリポジトリ。
 */
class SearchSettingsRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    companion object {
        /**
         * 検索エンジンのプリセット定義。
         */
        val SEARCH_PRESETS = listOf(
            "Google（google.com）" to "https://www.google.com/search?q={number}",
            "電話帳ナビ（telnavi.jp）" to "https://www.telnavi.jp/phone/{number}",
            "jpnumber電話番号検索（jpnumber.com）" to "https://www.jpnumber.com/searchnumber.do?number={number}",
            "カスタム" to ""
        )
        const val DEFAULT_SEARCH_URL = "https://www.google.com/search?q={number}"
    }

    /**
     * Web検索ボタン用のURLテンプレートを取得。
     */
    fun getSearchUrlTemplate(): String {
        return prefs.getString("search_url_template", DEFAULT_SEARCH_URL) ?: DEFAULT_SEARCH_URL
    }

    /**
     * Web検索ボタン用のURLテンプレートを保存。
     */
    fun setSearchUrlTemplate(url: String) {
        Log.i("SearchSettingsRepo", "Web検索URL設定変更: $url")
        prefs.edit().putString("search_url_template", url).apply()
    }
}
