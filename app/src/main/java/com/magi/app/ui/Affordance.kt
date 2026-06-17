package com.magi.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// [校正] テキストリンク状の操作を「用途別に強調を最適化したボタン」へ統一する共有部品。
//   追加=外枠＋＋アイコン / 編集=トーナル塗り / 削除=外枠(エラー) /
//   ダイアログ 確定=塗り・取消=外枠・破壊的=エラー塗り。
//   タッチ標的は Compose の最小インタラクティブサイズ(48dp)で担保。
private val CompactPad = PaddingValues(horizontal = 12.dp, vertical = 6.dp)

/** 一覧の「＋ ◯◯追加」: 追加と分かる外枠ボタン＋＋アイコン。 */
@Composable
fun AddRowButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
        Text(text)
    }
}

/** 一覧行の「編集」: トーナル塗りで控えめだが明確に押せる。 */
@Composable
fun EditRowButton(onClick: () -> Unit, enabled: Boolean = true, text: String = "編集") {
    FilledTonalButton(
        onClick = onClick, enabled = enabled,
        contentPadding = CompactPad, modifier = Modifier.heightIn(min = 44.dp),
    ) { Text(text) }
}

/** 一覧行の「削除」: エラー色の外枠で破壊的と分かる。 */
@Composable
fun DeleteRowButton(onClick: () -> Unit, enabled: Boolean = true, text: String = "削除") {
    OutlinedButton(
        onClick = onClick, enabled = enabled, contentPadding = CompactPad,
        modifier = Modifier.heightIn(min = 44.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) { Text(text) }
}

/** ダイアログの確定（肯定の主操作）: 塗りボタン。 */
@Composable
fun DialogConfirmButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled) { Text(text) }
}

/** ダイアログの取消/閉じる: 外枠ボタン。 */
@Composable
fun DialogDismissButton(onClick: () -> Unit, text: String = "キャンセル") {
    OutlinedButton(onClick = onClick) { Text(text) }
}

/** ダイアログの破壊的確定（削除など）: エラー色の塗りボタン。 */
@Composable
fun DialogDangerButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) { Text(text) }
}
