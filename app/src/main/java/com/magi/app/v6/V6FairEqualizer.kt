package com.magi.app.v6

import com.magi.app.model.MagiState

/**
 * [回数を揃えるボタン] グループ内のシフト回数のばらつき(fair)を、ほかの SOFT と引き換えに減らす。
 *
 * 通常の最適化・研磨は重み付き合計を悪くしない手しか採らないため、fair(重み1) は c1(重み4)や
 * 個人上下限(重み90/45)との取り合いに必ず負ける。このボタンは利用者が明示的に押したときだけ
 * 「fair を優先」する（重みは変えない＝HF77 に触れない・プレビューして適用/やめるを選ぶ）。
 *
 * 守るもの（入力の勤務表と比べて悪化させない）:
 *   - 必須(HARD) の件数
 *   - 個人の上下限(low + high) の件数
 *   - 希望セル（動かさない）・担当可否（担当できないシフトへは入れない）
 * 崩れてよいもの: c1・c3系・apt・weekly など上記以外の SOFT。
 *
 * 手: 同じ日・同じグループの2人のシフト入れ替え。人員(被覆)・グループの日次人数・グループの各シフト合計は
 *   不変なので fair の目標値 round(平均) も動かない。各ラウンドで fair を最も減らす手を選び、
 *   同点なら重み付き合計の小さい方を採る。
 */
object V6FairEqualizer {

    data class Result(
        val schedule: Array<IntArray>,
        val before: ViolationReport,
        val after: ViolationReport,
        val moves: Int,
        val timedOut: Boolean,
    )

    fun fairOf(r: ViolationReport): Int = r.breakdown["fair"] ?: 0
    fun rangeOf(r: ViolationReport): Int = (r.breakdown["low"] ?: 0) + (r.breakdown["high"] ?: 0)

    fun run(
        state: MagiState,
        schedule: Array<IntArray>,
        budgetMs: Long = 12_000L,
        maxMoves: Int = 200,
        shouldStop: () -> Boolean = { false },
    ): Result {
        val p = Problem(state)
        val work = normalizeSchedule(schedule, p)
        val before = UnifiedViolationChecker.check(state, work)
        val hardCap = before.hard
        val rangeCap = rangeOf(before)
        var cur = before
        var moves = 0
        val deadline = System.currentTimeMillis() + budgetMs
        var timedOut = false
        fun outOfTime(): Boolean {
            if (shouldStop() || System.currentTimeMillis() >= deadline) { timedOut = true; return true }
            return false
        }
        fun movable(i: Int, j: Int) = p.wish[i][j] < 0 && work[i][j] in 0 until p.K

        round@ while (moves < maxMoves && fairOf(cur) > 0) {
            if (outOfTime()) break
            val counts = countMatrix(p, work)
            // 目標値（グループ×シフトの round(平均)）。同グループ内の入れ替えでは不変。
            val tgt = Array(p.G) { g ->
                val mem = p.groupMembers[g]
                IntArray(p.K) { k ->
                    if (mem.size < 2) 0 else Math.round(mem.sumOf { counts[it][k] }.toDouble() / mem.size).toInt()
                }
            }
            fun inBucket(g: Int, k: Int) = p.bucket[g].contains(k)
            // 候補: 局所的に fair が下がる入れ替え（dev は |回数-目標| の変化量）。
            val cands = ArrayList<IntArray>()   // [delta, j, a, b]
            for (g in 0 until p.G) {
                val mem = p.groupMembers[g]
                if (mem.size < 2) continue
                for (j in 0 until p.T) for (x in mem.indices) {
                    val a = mem[x]
                    if (!movable(a, j)) continue
                    for (y in x + 1 until mem.size) {
                        val b = mem[y]
                        if (!movable(b, j)) continue
                        val sa = work[a][j]; val sb = work[b][j]
                        if (sa == sb || !p.canDo(a, sb) || !p.canDo(b, sa)) continue
                        var d = 0
                        if (inBucket(g, sa)) {
                            val t0 = tgt[g][sa]
                            d += kotlin.math.abs(counts[a][sa] - 1 - t0) - kotlin.math.abs(counts[a][sa] - t0)
                            d += kotlin.math.abs(counts[b][sa] + 1 - t0) - kotlin.math.abs(counts[b][sa] - t0)
                        }
                        if (inBucket(g, sb)) {
                            val t1 = tgt[g][sb]
                            d += kotlin.math.abs(counts[b][sb] - 1 - t1) - kotlin.math.abs(counts[b][sb] - t1)
                            d += kotlin.math.abs(counts[a][sb] + 1 - t1) - kotlin.math.abs(counts[a][sb] - t1)
                        }
                        if (d < 0) cands.add(intArrayOf(d, j, a, b))
                    }
                }
            }
            if (cands.isEmpty()) break
            cands.sortBy { it[0] }
            // 最も fair が下がる段から順に正式評価。採用可能な手が見つかった段を評価し終えたら決定。
            var best: IntArray? = null
            var bestRep: ViolationReport? = null
            var level = Int.MIN_VALUE
            for (c in cands) {
                if (best != null && c[0] != level) break
                if (outOfTime()) break
                val j = c[1]; val a = c[2]; val b = c[3]
                val sa = work[a][j]; val sb = work[b][j]
                work[a][j] = sb; work[b][j] = sa
                val rep = UnifiedViolationChecker.check(state, work)
                work[a][j] = sa; work[b][j] = sb
                val ok = rep.hard <= hardCap && rangeOf(rep) <= rangeCap && fairOf(rep) < fairOf(cur)
                if (!ok) continue
                val br = bestRep
                if (br == null || fairOf(rep) < fairOf(br) ||
                    (fairOf(rep) == fairOf(br) && rep.weightedScore < br.weightedScore - 1e-9)) {
                    best = c; bestRep = rep; level = c[0]
                }
            }
            val b0 = best ?: break
            val br = bestRep ?: break
            val j = b0[1]; val a = b0[2]; val b = b0[3]
            val sa = work[a][j]; work[a][j] = work[b][j]; work[b][j] = sa
            cur = br
            moves++
            if (timedOut) break@round
        }
        return Result(work, before, cur, moves, timedOut)
    }
}
