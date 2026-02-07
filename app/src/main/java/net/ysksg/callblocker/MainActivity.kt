package net.ysksg.callblocker

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import net.ysksg.callblocker.repository.ThemeRepository
import net.ysksg.callblocker.ui.MainScreen
import net.ysksg.callblocker.ui.theme.CallBlockerTheme

/**
 * アプリのメインアクティビティ。
 * UIのセットアップと画面表示のエントリーポイントとして機能します。
 * 実際のUI構築ロジックは [MainScreen] に委譲しています。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val themeRepo = remember { ThemeRepository(context) }
            
            // テーマ設定を監視
            val themeMode = produceState(initialValue = themeRepo.getThemeMode()) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                   if (key == "theme_mode") {
                       value = themeRepo.getThemeMode()
                   }
                }
                context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    .registerOnSharedPreferenceChangeListener(listener)
                
                awaitDispose {
                    context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                        .unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            val darkTheme = when (themeMode.value) {
                ThemeRepository.THEME_DARK -> true
                ThemeRepository.THEME_LIGHT -> false
                else -> isSystemInDarkTheme()
            }

            CallBlockerTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}
