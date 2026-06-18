package com.magi.app.v6

import com.magi.app.model.MagiState

/** 改善手の種類。CHANGE=1マスを別シフトに変更 / SWAP=同日2人のシフトを入れ替え。 */
enum class FixKind { CHANGE, SWAP }

/**
 * [改善提案] 1手で違反がどれだけ減るかを評価した候補。
 *  - CHANGE: staffA の day 日を fromShift -> toShift に変更（被覆・回数が変化。covO/covU/low/high/並びに有効）。
 *  - SWAP  : staffA と staffB の day 日のシフトを入れ替え（被覆は不変。並び/群ペア/希望に有効）。
 * diff: 家族キー -> 件数差（減少は負）。
 */
data class FixSuggestion(
    val kind: FixKind,
    val staffA: Int,
    val day: Int,
    val staffB: Int,        // SWAP のみ（CHANGE は -1）
    val toShiftIdx: Int,    // CHANGE のみ（SWAP は -1）
    val nameA: String,
    val nameB: String,
    val fromShift: String,  // CHANGE: A の現シフト / SWAP: A のシフト
    val toShift: String,    // CHANGE: 変更後 / SWAP: B のシフト
    val deltaHard: Int,
    val deltaTotal: Int,
    val diff: List<Pair<String, Int>>,
)

/**
 * 違反を減らす「1手」をユーザー向けに列挙する。最適化エンジンと同じ評価
 * （canDo 可否・希望ロック保護・UnifiedViolationChecker による被覆込み (hard,total,weighted) 辞書式改善）を用いる。
 * CHANGE（単一マス変更）と SWAP（同日ペア交換）の両方を統合し、効果順に返す。読取専用（盤面・データ不変）。
 * focusStaff!=null のときはそのスタッフが関わる手だけに絞る（違反タップ起点）。
 */
object FixSuggester {
    fun suggest(
        state: MagiState,
        schedule: Array<IntArray>,
        focusStaff: Int? = null,
        maxResults: Int = 8,
        maxEval: Int = 80000,
        deadlineMs: Long = 5000L,
    ): List<FixSuggestion> {
        val p = Problem(state)
        if (p.S < 1 || p.T < 1) return emptyList()
        val s = normalizeSchedule(schedule, p)
        val base = UnifiedViolationChecker.check(state, s)
        fun nm(i: Int) = state.staff.getOrNull(i)?.name ?: "#$i"
        fun sym(k: Int) = if (k >= 0) (state.shifts.getOrNull(k)?.kigou ?: "$k") else "—"
        fun diffOf(rep: ViolationReport): List<Pair<String, Int>> {
            val out = ArrayList<Pair<String, Int>>()
            for (k in (base.breakdown.keys + rep.breakdown.keys)) {
                val d = (rep.breakdown[k] ?: 0) - (base.breakdown[k] ?: 0)
                if (d != 0) out.add(k to d)
            }
            out.sortBy { it.second }
            return out
        }
        fun better(rep: ViolationReport): Boolean =
            rep.hard < base.hard ||
                (rep.hard == base.hard && rep.total < base.total) ||
                (rep.hard == base.hard && rep.total == base.total && rep.weightedScore < base.weightedScore)

        // (suggestion, deltaHard, deltaTotal, deltaWeighted)
        val found = ArrayList<Quad>()
        val start = System.currentTimeMillis()
        var evals = 0
        fun timeUp() = evals >= maxEval || System.currentTimeMillis() - start > deadlineMs

        // --- CHANGE: 1マスを別の担当可能シフトへ ---
        changeLoop@ for (i in 0 until p.S) {
            if (focusStaff != null && i != focusStaff) continue
            val allowed = p.allowedShiftsForStaff(i)
            for (j in 0 until p.T) {
                if (p.wish[i][j] >= 0) continue
                val a = s[i][j]
                for (k in allowed) {
                    if (k == a) continue
                    if (timeUp()) break@changeLoop
                    val cand = s.copy2D(); cand[i][j] = k
                    val rep = UnifiedViolationChecker.check(state, cand); evals++
                    if (!better(rep)) continue
                    val sug = FixSuggestion(FixKind.CHANGE, i, j, -1, k, nm(i), "", sym(a), sym(k),
                        rep.hard - base.hard, rep.total - base.total, diffOf(rep))
                    found.add(Quad(sug, rep.hard - base.hard, rep.total - base.total, rep.weightedScore - base.weightedScore))
                }
            }
        }
        // --- SWAP: 同日2人のシフトを入れ替え（被覆不変） ---
        swapLoop@ for (i in 0 until p.S) {
            for (i2 in i + 1 until p.S) {
                if (focusStaff != null && i != focusStaff && i2 != focusStaff) continue
                for (j in 0 until p.T) {
                    if (timeUp()) break@swapLoop
                    if (p.wish[i][j] >= 0 || p.wish[i2][j] >= 0) continue
                    val a = s[i][j]; val b = s[i2][j]
                    if (a == b || !p.canDo(i, b) || !p.canDo(i2, a)) continue
                    val cand = s.copy2D(); cand[i][j] = b; cand[i2][j] = a
                    val rep = UnifiedViolationChecker.check(state, cand); evals++
                    if (!better(rep)) continue
                    val sug = FixSuggestion(FixKind.SWAP, i, j, i2, -1, nm(i), nm(i2), sym(a), sym(b),
                        rep.hard - base.hard, rep.total - base.total, diffOf(rep))
                    found.add(Quad(sug, rep.hard - base.hard, rep.total - base.total, rep.weightedScore - base.weightedScore))
                }
            }
        }

        // 効果順（必須減 > 合計減 > 重み減）。同型の手は1つに絞って多様性を確保。
        found.sortWith(compareBy({ it.dHard }, { it.dTotal }, { it.dWeighted }))
        val seen = HashSet<String>()
        val result = ArrayList<FixSuggestion>()
        for (q in found) {
            val sug = q.sug
            val sig = if (sug.kind == FixKind.CHANGE)
                "C:${sug.staffA}:${sug.fromShift}:${sug.toShift}"
            else
                "S:${sug.staffA}:${sug.staffB}:${sug.fromShift}:${sug.toShift}"
            if (seen.add(sig)) result.add(sug)
            if (result.size >= maxResults) break
        }
        return result
    }

    private class Quad(val sug: FixSuggestion, val dHard: Int, val dTotal: Int, val dWeighted: Double)
}
