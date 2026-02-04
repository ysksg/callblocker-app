package net.ysksg.callblocker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
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
            CallBlockerTheme {
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
