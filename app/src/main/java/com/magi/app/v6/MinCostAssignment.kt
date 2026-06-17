package com.magi.app.v6

/**
 * 正方コスト行列に対する最小費用割当（Hungarian / Kuhn–Munkres、ポテンシャル法 O(n^3)）。
 * ソフト研磨の「日ごと厳密割当」で使用。禁止辺は十分大きなコスト(INF)で表現する。
 * 返り値 assign[row] = 割り当てられた列。
 */
object MinCostAssignment {
    const val INF: Long = Long.MAX_VALUE / 4

    fun solve(cost: Array<LongArray>): IntArray {
        val n = cost.size
        if (n == 0) return IntArray(0)
        require(cost.all { it.size == n }) { "cost must be square" }
        val u = LongArray(n + 1)
        val v = LongArray(n + 1)
        val p = IntArray(n + 1)        // p[col] = 割当済み row（1-indexed）。p[0] は探索の起点マーカ。
        val way = IntArray(n + 1)
        for (i in 1..n) {
            p[0] = i
            var j0 = 0
            val minv = LongArray(n + 1) { INF }
            val used = BooleanArray(n + 1)
            do {
                used[j0] = true
                val i0 = p[j0]
                var delta = INF
                var j1 = -1
                for (j in 1..n) {
                    if (!used[j]) {
                        val cur = cost[i0 - 1][j - 1] - u[i0] - v[j]
                        if (cur < minv[j]) { minv[j] = cur; way[j] = j0 }
                        if (minv[j] < delta) { delta = minv[j]; j1 = j }
                    }
                }
                for (j in 0..n) {
                    if (used[j]) { u[p[j]] += delta; v[j] -= delta } else minv[j] -= delta
                }
                j0 = j1
            } while (p[j0] != 0)
            do {
                val j1 = way[j0]
                p[j0] = p[j1]
                j0 = j1
            } while (j0 != 0)
        }
        val assign = IntArray(n)
        for (j in 1..n) if (p[j] in 1..n) assign[p[j] - 1] = j - 1
        return assign
    }
}
