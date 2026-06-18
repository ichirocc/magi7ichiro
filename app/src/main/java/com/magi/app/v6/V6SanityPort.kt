package com.magi.app.v6

import com.magi.app.model.C3Row
import com.magi.app.model.MagiState

/**
 * Deeper native port of V6 Web diagnostics: detectImpossibleWishes(),
 * buildLoadDataBitSummary(), buildShiftCountDiagnostic(), and the practical parts
 * of buildSanityCheck().  It intentionally returns structured Kotlin data so both
 * Compose and tests can consume the same result.
 */
data class ImpossibleWish(
    val staffIndex: Int,
    val dayIndex: Int,
    val staffName: String,
    val groupSymbol: String,
    val shiftSymbol: String,
    val reason: String,
)

/** 設定ミスの種別。UI でアイコン/誘導先タブを切り替えるのに使う。 */
enum class IssueKind { WISH, CONSTRAINT, DEMAND, RANGE }

/**
 * [設定ミスの誘導修正] 制約・希望の設定間違いを「どこが・なぜ・どう直すか」で人間に提示するための構造化項目。
 * - where: 場所（例「佐藤 7/3 希望『日勤』」「連続パターン『Dﾃ→A4』」）
 * - problem: 何が問題か（平易な日本語）
 * - fix: 具体的な直し方（どの画面で何をするか）
 */
data class SettingIssue(
    val kind: IssueKind,
    val where: String,
    val problem: String,
    val fix: String,
)

data class ShiftCountDiagnostic(
    val staffIndex: Int,
    val staffName: String,
    val shiftSymbol: String,
    val count: Int,
    val lo: Int?,
    val hi: Int?,
    val status: String,
)

data class V6SanityReport(
    val ok: Boolean,
    val warns: List<String>,
    val notes: List<String>,
    val loadDataBitSummary: String,
    val loadDataBitDetails: List<String>,
    val shiftCountDiagnostics: List<ShiftCountDiagnostic>,
    val impossibleWishes: List<ImpossibleWish>,
    val duplicateSeqConstraints: List<String>,
    val guidance: List<SettingIssue>,
)

object V6SanityPort {
    fun build(state: MagiState, schedule: Array<IntArray> = state.schedule.toIntArray2D()): V6SanityReport {
        val p = Problem(state)
        val s = normalizeSchedule(schedule, p)
        val warns = ArrayList<String>()
        val notes = ArrayList<String>()

        val invalidAssignments = invalidAssignmentCells(state, p, s)
        if (invalidAssignments.isNotEmpty()) {
            warns.add("担当不可または範囲外の配置が ${invalidAssignments.size} セルあります")
        }

        val impossible = detectImpossibleWishes(state, p)
        if (impossible.isNotEmpty()) {
            warns.add("実現不能な希望シフトが ${impossible.size} 件あります")
        }

        val dup = findDuplicateSeqConstraints(state)
        if (dup.isNotEmpty()) warns.add("連続パターン制約の重複が ${dup.size} 件あります")

        val badRanges = badStaffRanges(state, p)
        if (badRanges > 0) warns.add("staffRange の範囲外キーまたは lo>hi が ${badRanges} 件あります")

        val impossibleDemand = impossibleDemandDays(state, p)
        if (impossibleDemand.isNotEmpty()) {
            val head = ArrayList<String>()
            val lim = minOf(4, impossibleDemand.size)
            var idx = 0
            while (idx < lim) {
                head.add(impossibleDemand[idx])
                idx++
            }
            val suffix = if (impossibleDemand.size > 4) " …" else ""
            warns.add("担当可能人数を超える需要があります: ${head.joinToString(" / ")}$suffix")
        }

        var aptSet = 0
        for (row in state.groupShiftApt) {
            for (cell in row) {
                if (cell.trim().isNotEmpty()) aptSet++
            }
        }
        notes.add("groupShiftApt 適切回数: ${aptSet} 件")
        notes.add("shifts=${p.K} groups=${p.G} staff=${p.S} days=${p.T}")
        if (state.use2Patterns) notes.add("2世代需要(MIN=OR)が有効") else notes.add("需要はP1のみ")

        return V6SanityReport(
            ok = warns.isEmpty(),
            warns = warns,
            notes = notes,
            loadDataBitSummary = buildLoadDataBitSummary(state, p, s),
            loadDataBitDetails = buildLoadDataBitDetails(state, p),
            shiftCountDiagnostics = buildShiftCountDiagnostic(state, p, s),
            impossibleWishes = impossible,
            duplicateSeqConstraints = dup,
            guidance = buildGuidance(state, p),
        )
    }

    fun detectImpossibleWishes(state: MagiState, p: Problem = Problem(state)): List<ImpossibleWish> {
        val out = ArrayList<ImpossibleWish>()
        for ((key, k) in state.wishes) {
            val parts = key.split(',')
            val i = parts.getOrNull(0)?.toIntOrNull()
            val j = parts.getOrNull(1)?.toIntOrNull()
            val reason = when {
                i == null || j == null -> "希望キーが i,j 形式ではありません"
                i !in 0 until p.S || j !in 0 until p.T -> "スタッフまたは日付が範囲外です"
                k !in 0 until p.K -> "希望シフトが範囲外です"
                !p.canDo(i, k) -> "スタッフのグループでは担当不可です"
                else -> null
            }
            if (reason != null) {
                val si = i?.takeIf { it in 0 until p.S } ?: -1
                val gi = si.takeIf { it >= 0 }?.let { p.sgrp[it] } ?: -1
                out.add(
                    ImpossibleWish(
                        staffIndex = si,
                        dayIndex = j ?: -1,
                        staffName = state.staff.getOrNull(si)?.name ?: "#$si",
                        groupSymbol = state.groups.getOrNull(gi)?.kigou ?: "?",
                        shiftSymbol = state.shifts.getOrNull(k)?.kigou ?: k.toString(),
                        reason = reason,
                    )
                )
            }
        }
        return out.sortedWith(compareBy<ImpossibleWish> { it.staffIndex }.thenBy { it.dayIndex })
    }

    /**
     * [設定ミスの誘導修正] 制約・希望の設定間違いを、人間が直せる粒度（誰の/何日の/どのシフト/どの制約、
     * そして具体的な直し方）で列挙する。検出済みの構造化データを平易な日本語の指示文に変換するだけで、
     * 重み・データは一切変更しない（読み取り専用＝安全）。表示順は「直すべき度合い」が高い順。
     */
    fun buildGuidance(state: MagiState, p: Problem = Problem(state)): List<SettingIssue> {
        val out = ArrayList<SettingIssue>()

        // 1) 希望シフトの設定ミス（担当外・範囲外など）
        for (w in detectImpossibleWishes(state, p)) {
            val where = "${w.staffName} ${safeDayLabel(state.startDate, w.dayIndex)} 希望「${w.shiftSymbol}」"
            val fix = when {
                w.reason.contains("担当不可") ->
                    "この希望を取り消すか、設定(ws1)で${w.staffName}さんの担当に「${w.shiftSymbol}」を追加してください"
                w.reason.contains("範囲外") ->
                    "希望のシフト記号・日付が勤務表の範囲内かを確認してください"
                else -> "希望の入力（i,j形式）を確認してください"
            }
            out.add(SettingIssue(IssueKind.WISH, where, "実現できない希望です（${w.reason}）", fix))
        }

        // 2) 連続パターン制約の重複（例: c3n:Dﾃ→A4）
        for (d in findDuplicateSeqConstraints(state)) {
            val fam = c3FamilyJp(d.substringBefore(':'))
            val seq = d.substringAfter(':')
            out.add(SettingIssue(IssueKind.CONSTRAINT, "連続パターン「$seq」($fam)",
                "同じパターンが2重に登録されています", "連続パターン設定(ws4)で「$seq」の重複行を1つ削除してください"))
        }

        // 3) 需要 > 担当可能人数（その枠は誰をどう並べても必ず不足）
        for (j in 0 until p.T) for (k in 0 until p.K) {
            val need = p.need1[k][j]
            if (need <= 0) continue
            var capable = 0
            for (i in 0 until p.S) if (p.canDo(i, k)) capable++
            if (need > capable) {
                val sym = state.shifts.getOrNull(k)?.kigou ?: k.toString()
                out.add(SettingIssue(IssueKind.DEMAND, "${safeDayLabel(state.startDate, j)} $sym",
                    "必要${need}人ですが担当できるのは${capable}人だけです",
                    "担当できるスタッフを増やす(ws1)か、必要人数を${capable}人以下に下げてください(ws2)"))
            }
        }

        // 4) 回数レンジ(staffRange)の設定ミス
        for ((key, r) in state.staffRange) {
            val parts = key.split(',')
            val i = parts.getOrNull(0)?.toIntOrNull()
            val k = parts.getOrNull(1)?.toIntOrNull()
            val lo = r.lo.trim().toIntOrNull()
            val hi = r.hi.trim().toIntOrNull()
            val name = i?.let { state.staff.getOrNull(it)?.name } ?: "#$i"
            val sym = k?.let { state.shifts.getOrNull(it)?.kigou } ?: "$k"
            if (i == null || k == null || i !in 0 until p.S || k !in 0 until p.K) {
                out.add(SettingIssue(IssueKind.RANGE, "回数設定 $key", "対象スタッフ/シフトが範囲外です", "設定(ws1)で正しいスタッフ・シフトに付け直してください"))
                continue
            }
            if (lo != null && hi != null && lo > hi) {
                out.add(SettingIssue(IssueKind.RANGE, "$name の「$sym」回数", "下限$lo > 上限$hi で矛盾しています", "設定(ws1)で下限≤上限に直してください"))
            }
            if (lo != null && lo > 0 && !p.canDo(i, k)) {
                out.add(SettingIssue(IssueKind.RANGE, "$name の「$sym」回数", "担当できないシフトに下限${lo}が設定されています", "下限を0にするか、${name}さんの担当に「$sym」を追加してください(ws1)"))
            }
            if (lo != null && lo > p.T) {
                out.add(SettingIssue(IssueKind.RANGE, "$name の「$sym」回数", "下限${lo}が期間日数(${p.T}日)を超えています", "下限を${p.T}以下に直してください(ws1)"))
            }
        }

        // 5) 1人の各シフト下限の合計が期間日数を超える（割り当て不能）
        for (i in 0 until p.S) {
            var sumLo = 0
            for (k in 0 until p.K) {
                val lo = p.rangeLo[i][k]
                if (lo != Int.MIN_VALUE && lo > 0) sumLo += lo
            }
            if (sumLo > p.T) {
                val name = state.staff.getOrNull(i)?.name ?: "#$i"
                out.add(SettingIssue(IssueKind.RANGE, "$name の回数下限の合計",
                    "各シフトの下限の合計が${sumLo}で、期間日数(${p.T}日)を超えています",
                    "どれかのシフトの下限を下げてください（合計を${p.T}以下に）(ws1)"))
            }
        }

        return out
    }

    private fun c3FamilyJp(fam: String): String = when (fam) {
        "c3" -> "必須MUST"
        "c3n" -> "禁止FORBIDDEN"
        "c3m" -> "希望Want"
        "c3mn" -> "回避Hate"
        else -> fam
    }

    private fun buildLoadDataBitSummary(state: MagiState, p: Problem, schedule: Array<IntArray>): String {
        var assigned = 0
        for (row in schedule) {
            for (v in row) {
                if (v in 0 until p.K) assigned++
            }
        }
        val possible = p.S * p.T
        var allowBits = 0
        for (g in 0 until p.G) {
            allowBits += p.bucket.getOrNull(g)?.size ?: 0
        }
        val wishCount = state.wishes.size
        val rangeCount = state.staffRange.size
        return "LoadDataBit: staffN=${p.S} termT=${p.T} shiftK=${p.K} assigned=$assigned/$possible allowBits=$allowBits wishes=$wishCount ranges=$rangeCount"
    }

    private fun buildLoadDataBitDetails(state: MagiState, p: Problem): List<String> {
        val out = ArrayList<String>()
        for (g in 0 until p.G) {
            val allowedParts = ArrayList<String>()
            for (k in p.bucket[g]) {
                allowedParts.add(state.shifts.getOrNull(k)?.kigou ?: k.toString())
            }
            val allowed = allowedParts.joinToString(" ")
            var members = 0
            for (staff in state.staff) {
                if (staff.groupIdx == g) members++
            }
            out.add("Group ${state.groups.getOrNull(g)?.kigou ?: g}: members=$members allowed=[$allowed]")
        }
        return out
    }

    private fun buildShiftCountDiagnostic(state: MagiState, p: Problem, schedule: Array<IntArray>): List<ShiftCountDiagnostic> {
        val counts = countMatrix(p, schedule)
        val out = ArrayList<ShiftCountDiagnostic>()
        for (i in 0 until p.S) for (k in 0 until p.K) {
            val lo = p.rangeLo[i][k].takeIf { it != Int.MIN_VALUE }
            val hi = p.rangeHi[i][k].takeIf { it != Int.MAX_VALUE }
            if (lo == null && hi == null) continue
            val n = counts[i][k]
            val status = when {
                lo != null && n < lo -> "LOW"
                hi != null && n > hi -> "HIGH"
                else -> "OK"
            }
            out.add(ShiftCountDiagnostic(i, state.staff.getOrNull(i)?.name ?: "#$i", state.shifts.getOrNull(k)?.kigou ?: k.toString(), n, lo, hi, status))
        }
        return out.sortedWith(compareBy<ShiftCountDiagnostic> { it.status != "LOW" && it.status != "HIGH" }.thenBy { it.staffIndex })
    }

    private fun invalidAssignmentCells(state: MagiState, p: Problem, schedule: Array<IntArray>): List<String> {
        val out = ArrayList<String>()
        for (i in 0 until p.S) for (j in 0 until p.T) {
            val k = schedule[i][j]
            if (k !in 0 until p.K) out.add("$i,$j=範囲外($k)")
            else if (!p.canDo(i, k)) out.add("$i,$j=${state.shifts.getOrNull(k)?.kigou ?: k}")
        }
        return out
    }

    private fun badStaffRanges(state: MagiState, p: Problem): Int {
        var bad = 0
        for ((key, r) in state.staffRange) {
            val parts = key.split(',')
            val i = parts.getOrNull(0)?.toIntOrNull()
            val k = parts.getOrNull(1)?.toIntOrNull()
            val lo = r.lo.trim().toIntOrNull()
            val hi = r.hi.trim().toIntOrNull()
            if (i == null || k == null || i !in 0 until p.S || k !in 0 until p.K) bad++
            if (lo != null && hi != null && lo > hi) bad++
        }
        return bad
    }

    private fun impossibleDemandDays(state: MagiState, p: Problem): List<String> {
        val out = ArrayList<String>()
        for (j in 0 until p.T) for (k in 0 until p.K) {
            val need = p.need1[k][j]
            if (need <= 0) continue
            var capable = 0
            for (i in 0 until p.S) {
                if (p.canDo(i, k)) capable++
            }
            if (need > capable) out.add("${safeDayLabel(state.startDate, j)} ${state.shifts.getOrNull(k)?.kigou ?: k}: need=$need capable=$capable")
        }
        return out
    }

    private fun findDuplicateSeqConstraints(state: MagiState): List<String> {
        val out = ArrayList<String>()
        collectDuplicateSeq("c3", state.cons3, out)
        collectDuplicateSeq("c3n", state.cons3n, out)
        collectDuplicateSeq("c3m", state.cons3m, out)
        collectDuplicateSeq("c3mn", state.cons3mn, out)
        return out
    }

    private fun collectDuplicateSeq(name: String, rows: List<C3Row>, out: MutableList<String>) {
        val seen = HashSet<String>()
        for (r in rows) {
            val parts = ArrayList<String>()
            for (item in r.pattern) {
                if (item.isBlank()) break
                parts.add(item)
            }
            val key = parts.joinToString("→")
            if (key.isBlank()) continue
            if (!seen.add(key)) out.add("$name:$key")
        }
    }
}

private fun safeDayLabel(startDate: String, offset: Int): String = try {
    val d = java.time.LocalDate.parse(startDate).plusDays(offset.toLong())
    val wd = "月火水木金土日"[d.dayOfWeek.value - 1]
    "${d.monthValue}/${d.dayOfMonth}($wd)"
} catch (_: Exception) {
    "${offset + 1}日"
}
