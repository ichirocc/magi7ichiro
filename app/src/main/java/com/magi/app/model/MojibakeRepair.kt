package com.magi.app.model

/**
 * 二重エンコード文字化け（UTF-8 のバイト列を Latin-1/CP1252 として読み、再び UTF-8 で保存した
 * いわゆる「å¤æ³…」型）の自動修復。
 *
 * MAGI 自身の読み書きは UTF-8 固定なので化けは作らないが、外部ツールや旧 Web 書き出しで
 * 二重エンコードされた JSON/CSV を読み込んだ場合に、表示が文字化けする。これを安全に元へ戻す。
 *
 * 安全策（誤変換を避ける）:
 *  - 既に正しい多バイト文字(U+00FF 超＝本来の日本語)が含まれる → 化けではない → そのまま。
 *  - 0x80..0xFF の拡張ラテン文字が無い(ASCII のみ) → 変換不要 → そのまま。
 *  - 変換結果に U+FFFD(置換文字)が増える(＝不正な UTF-8 並び＝本物の Latin-1) → 失敗とみなし元を返す。
 *  これらにより、二重エンコード UTF-8 のときだけ復元され、本物の Latin-1 テキストは保護される。
 */
object MojibakeRepair {
    fun repair(s: String): String {
        if (s.isEmpty()) return s
        if (s.any { it.code > 0xFF }) return s                 // 本物の日本語等が既にある
        if (s.none { it.code in 0x80..0xFF }) return s         // ASCII のみ
        return try {
            val decoded = String(s.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
            val before = s.count { it == '�' }
            val after = decoded.count { it == '�' }
            // 置換文字が増えない かつ 実際に多バイト文字へ復元できた場合のみ採用
            if (after <= before && decoded.any { it.code > 0xFF }) decoded else s
        } catch (_: Exception) {
            s
        }
    }

    /** 修復が必要(=変化する)かの判定。ログ表示用。 */
    fun looksMojibake(s: String): Boolean = repair(s) != s
}
