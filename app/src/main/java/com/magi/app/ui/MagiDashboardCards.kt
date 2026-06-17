package com.magi.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import java.time.LocalDate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magi.app.v6.V6PortReport
import com.magi.app.v6.V6Algorithm
import com.magi.app.v6.CoverageVerdict
import com.magi.app.v6.MirrorKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput

/**
 * CSVのバイト列を文字列へ復号する。妥当な UTF-8 ならそれを採用し、そうでなければ日本の Excel CSV で
 * 一般的な CP932(Shift-JIS) とみなす。先頭の BOM は除去する。これにより Shift-JIS の勤務表CSVが
 * 文字化けせず取り込める（UTF-8 として bytes を読むと壊れていた）。
 */

@Composable
internal fun GuidedFixDialog(ui: UiState, vm: MagiViewModel, onDismiss: () -> Unit, onRerun: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shortfalls = ui.coverageDiag?.shortfalls ?: emptyList()
    val target = shortfalls.firstOrNull { it.verdict == CoverageVerdict.FIXABLE && it.miss > 0 }
    val infeasible = shortfalls.filter { it.verdict == CoverageVerdict.INFEASIBLE }
    val allDone = target == null && infeasible.isEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (allDone) "直し終わりました！" else "なおすのを手伝います") },
        text = {
            Column(
                Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    target != null -> {
                        Text("${target.dayLabel} の「${target.shiftSymbol}」が ${target.miss}人 足りません。",
                            fontWeight = FontWeight.Bold)
                        Text("この日に動かせる人がいます。だれかを「${target.shiftSymbol}」に入れますか？",
                            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        val cands = remember(target.dayIndex, target.shiftIndex, ui.coverageDiag) {
                            vm.shortageFixCandidates(target.dayIndex, target.shiftIndex)
                        }
                        if (cands.isEmpty()) {
                            Text("いま動かせる人がいません。別の日を見直すか、データを確認してください。", color = cs.error)
                        } else {
                            cands.take(8).forEach { c ->
                                Button(
                                    onClick = { vm.setCell(c.staffIndex, target.dayIndex, target.shiftIndex) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 2.dp),
                                ) {
                                    val tail = if (c.fromRest) "（休み）" else ""
                                    // 長い氏名でも切れないよう2行まで折り返し（文字欠け防止）。
                                    Text("${c.name}$tail を「${target.shiftSymbol}」に入れる",
                                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Text("入れたら「元に戻す」でいつでも取り消せます。", fontSize = 11.sp, color = cs.onSurfaceVariant)
                        }
                    }
                    infeasible.isNotEmpty() -> {
                        Text("これ以上は自動で埋められません。", fontWeight = FontWeight.Bold)
                        infeasible.take(4).forEach {
                            Text("・${it.dayLabel}「${it.shiftSymbol}」：${it.reason}",
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        Text("人を増やすか、担当できるシフトや希望を見直すと直せます。", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                    else -> {
                        Text("人手が足りない日はなくなりました。仕上げにもう一度つくると全体が整います。")
                    }
                }
            }
        },
        confirmButton = {
            if (allDone) DialogConfirmButton("もう一度つくる", onClick = onRerun)
            else DialogDismissButton(onClick = onDismiss, text = "閉じる")
        },
        // [dogfooding] 修正中は「閉じる」だけ（やめる＝同じ動作の重複ボタンを排除）。完了時のみ第2ボタンを出す。
        dismissButton = { if (allDone) DialogDismissButton(onClick = onDismiss, text = "閉じる") },
    )
}

/** [operator_ux §3] 思考誘導カードの1状態分のプラン（文言・色・大ボタン・補助）。 */

internal class OpNextPlan(
    val container: Color, val fg: Color, val headline: String,
    val bigLabel: String, val bigAction: () -> Unit, val bigEnabled: Boolean,
    val helperLabel: String?, val helperAction: () -> Unit,
)

/**
 * [operator_ux §3] 思考誘導ホームの「次にやること」カード。
 * IT中学生レベルのオペレーター向け：専門用語ゼロ・大ボタン1つ・色で意味（緑=できた/黄=もう少し/赤=気をつけて）。
 * いまの状態（未作成／組立中／配れる／もう少し／埋められない）で文言と主ボタンが自動で変わる。
 */

@Composable
internal fun OperatorNextActionCard(
    ui: UiState,
    onMake: () -> Unit,      // 勤務表をつくる（最適化）
    onDraft: () -> Unit,     // 下書きをつくる（簡易作成）
    onStop: () -> Unit,      // やめる（停止）
    onExport: () -> Unit,    // 印刷・書き出し / そのまま配る（CSV書き出し）
    onSchedule: () -> Unit,  // 中身を見る（勤務表へ）
    onFix: () -> Unit,       // なおすのを手伝って（勤務表で手直し）
    onSetup: () -> Unit,     // データを見直す（編集へ）
) {
    val cs = MaterialTheme.colorScheme
    val infeasible = ui.coverageDiag?.allInfeasible == true
    val shortDays = ui.coverageDiag?.shortfalls?.map { it.dayIndex }?.distinct()?.size ?: 0
    val worstDay = ui.coverageDiag?.shortfalls?.firstOrNull()?.dayLabel

    // [M3] 成功=tertiary / 注意=error / 主操作=primary はテーマロール。警告のみ独自トークンに集約。
    val (amber, onAmber) = magiWarnColors()

    val plan = when {
        ui.running -> {
            val remainMin = ((ui.budgetSec * 1000L - ui.elapsedMs).coerceAtLeast(0L) / 60_000L) + 1
            // [校正] 「やめる」は下部コマンドバーに常設済み。カード側の補助ボタンは重複のため出さない。
            OpNextPlan(cs.primaryContainer, cs.onPrimaryContainer,
                "いま、コンピューターが組んでいます。\nあと約 ${remainMin} 分。閉じても大丈夫です。",
                "", {}, false, null, onStop)
        }
        !ui.hasResult -> OpNextPlan(cs.primaryContainer, cs.onPrimaryContainer,
            "② ボタンひとつで、勤務表を作ります。",
            "勤務表をつくる", onMake, true, "下書きをつくる", onDraft)
        ui.bestHard == 0L -> OpNextPlan(cs.tertiaryContainer, cs.onTertiaryContainer,
            "③ できました！ そのまま配れます。",
            "印刷・書き出し", onExport, true, "中身を見る", onSchedule)
        infeasible -> OpNextPlan(cs.errorContainer, cs.onErrorContainer,
            "このデータでは、ここは埋められません。" + (worstDay?.let { "（例：$it）" } ?: ""),
            "データを見直す", onSetup, true, "そのまま配る", onExport)
        else -> OpNextPlan(amber, onAmber,
            "もう少しです。" + (worstDay?.let { "$it が人手不足です。" } ?: "人手が足りない日があります。"),
            "なおすのを手伝って", onFix, true, "もう一度つくる", onMake)
    }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = plan.container)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(plan.headline, style = MaterialTheme.typography.titleLarge, color = plan.fg, fontWeight = FontWeight.Bold)
            // 数字は必ず言葉つきで意味を添える（operator_ux §6）。
            Text(
                "人手が足りない日：${shortDays}日 ・ できあがり度：${ui.satisfaction}点",
                style = MaterialTheme.typography.bodyMedium, color = plan.fg,
            )
            if (ui.running) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = plan.fg)
                    Text("組み立て中…", color = plan.fg, modifier = Modifier.weight(1f))
                }
            }
            if (plan.bigEnabled) {
                Button(onClick = plan.bigAction, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(plan.bigLabel, style = MaterialTheme.typography.titleMedium)
                }
            }
            plan.helperLabel?.let { hl ->
                // [校正] 補助操作もテキストリンクではなく外枠ボタンに（カード地色でも見えるよう枠色=前景色）。
                OutlinedButton(
                    onClick = plan.helperAction,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = plan.fg),
                    border = BorderStroke(1.dp, plan.fg.copy(alpha = 0.5f)),
                ) { Text(hl) }
            }
        }
    }
}

/** [対象月の選択] 勤務表を作る月を前月/翌月/今月で選ぶ。変更でその月の日数に合わせて表を作り直す。 */

@Composable
internal fun CopilotCard(ui: UiState, onGoEdit: () -> Unit) {
    // [冗長性削減] できあがり度・進捗は OperatorNextActionCard が表示するため、ここは助言/警告だけに専念。
    val cs = MaterialTheme.colorScheme
    val show = ui.impossibleWishCount > 0 || ui.copilotHint != null || (ui.polishExhausted && !ui.running)
    if (!show) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 担当外など実現不能な希望の警告（Web版の担当外希望警告に相当）
            if (ui.impossibleWishCount > 0) {
                Surface(color = cs.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("⚠ 実現できない希望が ${ui.impossibleWishCount} 件（担当外シフトなど）。配布前に見直しを。",
                            color = cs.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = onGoEdit, modifier = Modifier.heightIn(min = 48.dp)) { Text("希望シフトを編集") }
                    }
                }
            }
            // ガチャ操作の助言＋修正導線（NextActionBar相当）
            ui.copilotHint?.let {
                Surface(color = cs.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("💡 $it", color = cs.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = onGoEdit, modifier = Modifier.heightIn(min = 48.dp)) { Text("編集タブで見直す") }
                    }
                }
            }
            if (ui.polishExhausted && !ui.running) {
                Surface(color = cs.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
                    Text("✓ 必須条件は満たしています。残りの微調整は勤務表タブでの手修正が早い場合があります。",
                        color = cs.onTertiaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * 人員不足(covU)が残るときだけ表示する原因診断カード。
 * 各不足枠を「充足不可（データ上どう割り当てても埋まらない）」か
 * 「充足可能（枠は足りる＝最適化が未到達）」に切り分けて、配布前の判断材料にする。
 */

@Composable
internal fun CoverageDiagnosisCard(ui: UiState) {
    val diag = ui.coverageDiag ?: return
    if (!diag.hasShortage) return
    val cs = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("人員不足の原因", style = MaterialTheme.typography.titleMedium)
            val headline = when {
                diag.allInfeasible -> "不足 ${diag.totalShortfall} 件は全て充足不可。今のデータでは満たせません（想定内）。"
                diag.infeasibleSlots == 0 -> "不足 ${diag.totalShortfall} 件は枠が足りています。再実行や設定の見直しで解消し得ます。"
                else -> "不足 ${diag.totalShortfall} 件 — 充足不可 ${diag.infeasibleSlots} 枠 / 充足可能 ${diag.fixableSlots} 枠。"
            }
            Text(headline, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            for (s in diag.shortfalls.take(6)) {
                val infeasible = s.verdict == CoverageVerdict.INFEASIBLE
                val container = if (infeasible) cs.errorContainer else cs.secondaryContainer
                val onContainer = if (infeasible) cs.onErrorContainer else cs.onSecondaryContainer
                Surface(color = container, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${s.dayLabel}  ${s.shiftSymbol}  必要${s.need}/現状${s.got}（不足${s.miss}）",
                                color = onContainer, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            MagiTagChip(
                                text = if (infeasible) "充足不可" else "充足可能",
                                color = if (infeasible) MagiAccent.red else MagiAccent.blue,
                            )
                        }
                        Text(s.reason, color = onContainer, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (diag.shortfalls.size > 6) {
                Text("ほか ${diag.shortfalls.size - 6} 枠（詳細はログ出力を参照）",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }
}


@Composable
internal fun V6DashboardCard(v6: V6PortReport?) {
    if (v6 == null) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("V6 1ヶ月俯瞰", fontWeight = FontWeight.Bold)
            Text(
                "人員の穴・負荷の偏り・入力ミスを勤務表から直接集計します。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            // §5.4 上部: 充足率(coverage%)を大数値ゲージで（必要人数のうち満たせた割合）
            v6.coveragePct?.let { pct ->
                val tint = if (pct >= 100) MaterialTheme.colorScheme.tertiary else if (pct >= 90) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                MagiScoreGauge(
                    score = pct,
                    max = 100,
                    label = "人員充足率",
                    sub = "必要人数 ${v6.demand} のうち満たせた割合",
                    accent = tint,
                )
                Spacer(Modifier.height(14.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                BigStat("HARD Core", v6.hardCore.toString(), Modifier.weight(1f))
                BigStat("Guard", v6.hardGuard.toString(), Modifier.weight(1f))
                BigStat("充足", v6.coveragePct?.let { "$it%" } ?: "-", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (v6.topRiskShortage > 0) "最優先: ${v6.topRiskLabel} に不足 ${v6.topRiskShortage} 枠" else "最優先: 人員不足なし",
                color = if (v6.topRiskShortage > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                v6.dayRisks.forEach { d -> RiskChip(d.label, d.shortage, d.detail) }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Apt=${"%.2f".format(v6.aptPenalty)} / Equalize=${"%.2f".format(v6.equPenalty)} / Demand=${v6.demand} / covU=${v6.covU}",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (v6.sanityWarnings.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                v6.sanityWarnings.take(3).forEach {
                    Text("⚠ $it", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("負荷プロフィール", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            v6.staffProfiles.take(5).forEach { st ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${st.name} ${st.groupSymbol}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("違反${st.violationCount} / 出勤${st.workCount} / ${st.workloadText}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}


@Composable
internal fun RiskChip(label: String, shortage: Int, detail: String) {
    val cs = MaterialTheme.colorScheme
    val (warnBg, warnFg) = magiWarnColors()
    val bg: Color; val fg: Color
    when {
        shortage <= 0 -> { bg = cs.tertiaryContainer; fg = cs.onTertiaryContainer }
        shortage == 1 -> { bg = warnBg; fg = warnFg }
        else -> { bg = cs.errorContainer; fg = cs.onErrorContainer }
    }
    Box(
        Modifier
            .width(76.dp)
            .background(bg, RoundedCornerShape(16.dp))
            .padding(horizontal = 7.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 11.sp, color = fg, maxLines = 1)
            Text(if (shortage > 0) "不足$shortage" else "OK", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg)
            if (detail.isNotBlank()) Text(detail, fontSize = 10.sp, color = fg.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}


@Composable
internal fun BreakdownCard(ui: UiState) {
    val labels = mapOf(
        "groupViol" to "グループ不整合", "pref" to "希望違反", "covU" to "人員不足", "c3n" to "禁止の並び",
        "low" to "下限割れ", "high" to "上限超過",
        "c1" to "窓の要件", "c2" to "個人の合計", "c3" to "必須の並び", "c3m" to "推奨の並び",
        "c3mn" to "回避の並び", "c41" to "群のレンジ", "c42" to "群ペア", "covO" to "過剰な配置",
    )
    var criticalOnly by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("違反の内訳", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("重大のみ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Switch(checked = criticalOnly, onCheckedChange = { criticalOnly = it })
            }
            BreakdownGroup("必須（満たすべき）", listOf("groupViol", "pref", "covU", "c3n"), 2, ui, labels)
            if (!criticalOnly) {
                BreakdownGroup("人数の範囲", listOf("low", "high"), 1, ui, labels)
                BreakdownGroup("任意（できれば）", listOf("c1", "c2", "c3", "c3m", "c3mn", "c41", "c42", "covO"), 0, ui, labels)
            }
        }
    }
}


@Composable
internal fun BreakdownGroup(title: String, keys: List<String>, severity: Int, ui: UiState, labels: Map<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        keys.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key -> SeverityChip(labels[key] ?: key, ui.breakdown[key] ?: 0, severity, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}


@Composable
internal fun SeverityChip(label: String, count: Int, severity: Int, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val active = count > 0
    val container: Color; val onContainer: Color
    when {
        !active -> { container = cs.surfaceVariant; onContainer = cs.onSurfaceVariant }
        severity >= 2 -> { container = cs.errorContainer; onContainer = cs.onErrorContainer }
        severity == 1 -> { container = cs.secondaryContainer; onContainer = cs.onSecondaryContainer }
        else -> { container = cs.primaryContainer; onContainer = cs.onPrimaryContainer }
    }
    Surface(color = container, shape = MaterialTheme.shapes.small, modifier = modifier.heightIn(min = 48.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = onContainer,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(count.toString(), style = MaterialTheme.typography.titleMedium, color = onContainer)
        }
    }
}

/**
 * [B1] 勤務表の「結果(読取ws6)／編集中(ws7)」モード切替カード。
 * 既存の 7日/カレンダー/1ヶ月 切替の上に置く。結果モードは誤編集防止のため読取専用。
 */

@Composable
internal fun BigStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(
            Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}


@Composable
internal fun AlternativesCard(ui: UiState, onApply: (Int) -> Unit) {
    if (ui.alternatives.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("他の案（${ui.alternatives.size}）", style = MaterialTheme.typography.titleMedium)
            Text("並列探索で見つかった、採用案以外の候補です。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            ui.alternatives.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(s, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { onApply(i) }, enabled = !ui.running, modifier = Modifier.heightIn(min = 48.dp)) { Text("採用") }
                }
            }
        }
    }
}


@Composable
internal fun WishApplyCard(ui: UiState, onApply: () -> Unit) {
    if (!ui.loaded) return
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("希望シフトを反映", fontWeight = FontWeight.Bold)
                Text("登録済みの希望を勤務表へ上書きします（元に戻せます）。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onApply, enabled = !ui.running, modifier = Modifier.heightIn(min = 48.dp)) { Text("希望を反映する") }
        }
    }
}
