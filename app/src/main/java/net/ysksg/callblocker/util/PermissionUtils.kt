package net.ysksg.callblocker.util

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 権限やロールに関連するユーティリティ関数。
 */
object PermissionUtils {

    /**
     * Call Screening Role (着信ブロック権限) を保持しているか確認します。
     */
    fun checkCallScreeningRole(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
            return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        }
        return true // Android 9以下はロール概念がないためtrue扱い（または別途権限チェックが必要だが、本アプリはQ以上推奨）
    }

    /**
     * Call Screening Role を要求します。
     */
    fun requestCallScreeningRole(activity: Activity, launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(Context.ROLE_SERVICE) as RoleManager
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            launcher.launch(intent)
        }
    }
}
