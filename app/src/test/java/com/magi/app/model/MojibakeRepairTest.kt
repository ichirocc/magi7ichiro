package com.magi.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MojibakeRepairTest {

    /** UTF-8 を Latin-1 として読んでしまった「二重エンコード」状態を再現する。 */
    private fun doubleEncode(s: String): String =
        String(s.toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)

    @Test
    fun repairsDoubleEncodedJapaneseName() {
        val original = "古泉 健一"
        assertEquals(original, MojibakeRepair.repair(doubleEncode(original)))
    }

    @Test
    fun repairsDoubleEncodedWithinJson() {
        val json = """{"staff":[{"name":"山本 昌幸","groupIdx":0}],"shifts":[{"kigou":"夜勤"}]}"""
        assertEquals(json, MojibakeRepair.repair(doubleEncode(json)))
    }

    @Test
    fun leavesCleanJapaneseUntouched() {
        val s = "古泉 健一 ・ 夜勤 ・ 休"
        assertEquals(s, MojibakeRepair.repair(s))
        assertFalse(MojibakeRepair.looksMojibake(s))
    }

    @Test
    fun leavesAsciiUntouched() {
        val s = """{"name":"A4","count":12}"""
        assertEquals(s, MojibakeRepair.repair(s))
    }

    @Test
    fun detectsMojibake() {
        assertTrue(MojibakeRepair.looksMojibake(doubleEncode("佐藤 美和")))
    }

    @Test
    fun idempotentOnRepairedText() {
        val original = "鈴木 隆"
        val once = MojibakeRepair.repair(doubleEncode(original))
        assertEquals(once, MojibakeRepair.repair(once)) // 既に正常 → 二重適用しても不変
    }
}
