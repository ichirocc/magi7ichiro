package com.magi.app.v6

import com.magi.app.model.C3Row
import com.magi.app.model.Group
import com.magi.app.model.MagiState
import com.magi.app.model.Shift
import com.magi.app.model.Staff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V6SanityPortTest {
    @Test fun detectsImpossibleWishAndInvalidAssignment() {
        val st = MagiState(
            startDate = "2026-06-01",
            endDate = "2026-06-02",
            shifts = listOf(Shift("休", "休", "", ""), Shift("A", "A", "1", "")),
            groups = listOf(Group("G", "G")),
            staff = listOf(Staff("s0", 0)),
            use2Patterns = false,
            groupShift = listOf(listOf(1, 0)),
            groupShiftApt = listOf(listOf("", "")),
            schedule = listOf(listOf(1, 0)),
            wishes = mapOf("0,0" to 1),
            staffRange = emptyMap(),
            needDay1 = emptyMap(),
            needDay2 = emptyMap(),
            cons1 = emptyList(), cons2 = emptyList(), cons3 = emptyList(), cons3n = emptyList(),
            cons3m = emptyList(), cons3mn = emptyList(), cons41 = emptyList(), cons42 = emptyList(),
        )
        val rep = V6SanityPort.build(st)
        assertEquals(1, rep.impossibleWishes.size)
        assertTrue(rep.warns.any { it.contains("実現不能") })
        assertTrue(rep.warns.any { it.contains("担当不可") })
    }

    private fun floorState(wishes: Map<String, Int>, cons3n: List<C3Row>, groupShift: List<List<Int>> = listOf(listOf(1, 1, 1))) = MagiState(
        startDate = "2026-06-01",
        endDate = "2026-06-05",
        shifts = listOf(Shift("休", "休", "", ""), Shift("C", "C", "", ""), Shift("A", "A", "", "")),
        groups = listOf(Group("G", "G")),
        staff = listOf(Staff("s0", 0)),
        use2Patterns = false,
        groupShift = groupShift,
        groupShiftApt = listOf(listOf("", "", "")),
        schedule = listOf(listOf(0, 0, 0, 0, 0)),
        wishes = wishes,
        staffRange = emptyMap(),
        needDay1 = emptyMap(),
        needDay2 = emptyMap(),
        cons1 = emptyList(), cons2 = emptyList(), cons3 = emptyList(), cons3n = cons3n,
        cons3m = emptyList(), cons3mn = emptyList(), cons41 = emptyList(), cons42 = emptyList(),
    )

    @Test fun hardFloorCountsWishWishForbiddenWindows() {
        // 1日=C, 2日=A の希望が禁止の並び C→A に当たる＝1件は必ず残る。
        val st = floorState(mapOf("0,0" to 1, "0,1" to 2), listOf(C3Row(listOf("C", "A"))))
        assertEquals(1, V6SanityPort.provableHardFloor(st))
        // 下限は実際の HARD を超えない（希望どおりの勤務表で c3n=1）。
        val sched = arrayOf(intArrayOf(1, 2, 0, 0, 0))
        assertTrue(UnifiedViolationChecker.check(st, sched).hard >= 1)
    }

    @Test fun hardFloorDoesNotDoubleCountOverlapsOrDuplicates() {
        // C→A→C: 窓(1-2日)と(2-3日)は2日目を共有＝2日目の希望1件で両方消えうる → 1件。重複登録も1件。
        val st = floorState(
            mapOf("0,0" to 1, "0,1" to 2, "0,2" to 1),
            listOf(C3Row(listOf("C", "A")), C3Row(listOf("A", "C")), C3Row(listOf("C", "A"))),
        )
        assertEquals(1, V6SanityPort.provableHardFloor(st))
    }

    @Test fun hardFloorIgnoresImpossibleWishesAndNonWishCells() {
        // A は担当不可＝不可能希望は HARD に数えないので下限にも入れない。
        val impossible = floorState(mapOf("0,0" to 1, "0,1" to 2), listOf(C3Row(listOf("C", "A"))), listOf(listOf(1, 1, 0)))
        assertEquals(0, V6SanityPort.provableHardFloor(impossible))
        // 片方しか希望が無ければ避けられる → 0。
        val half = floorState(mapOf("0,0" to 1), listOf(C3Row(listOf("C", "A"))))
        assertEquals(0, V6SanityPort.provableHardFloor(half))
    }
}
