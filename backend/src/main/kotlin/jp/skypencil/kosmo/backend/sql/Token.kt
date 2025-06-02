package jp.skypencil.kosmo.backend.sql

/**
 * SQL文字列のトークンを表すsealed class。
 * 各トークン種別ごとに適切なデータ構造を提供し、将来の拡張性も考慮している。
 */
sealed class Token {
    /**
     * SQLキーワード（SELECT, FROM, WHERE等）
     */
    data class Keyword(
        val value: String,
    ) : Token()

    /**
     * 識別子（テーブル名、カラム名等）
     */
    data class Identifier(
        val name: String,
    ) : Token()

    /**
     * 数値リテラル
     */
    data class NumberLiteral(
        val value: String,
    ) : Token()

    /**
     * 文字列リテラル
     */
    data class StringLiteral(
        val value: String,
    ) : Token()

    /**
     * 記号類（カンマ、セミコロン、括弧、演算子等）
     */
    data class Symbol(
        val value: String,
    ) : Token()

    /**
     * ホワイトスペース（通常は除外されるが、必要に応じて保持可能）
     */
    data class Whitespace(
        val value: String,
    ) : Token()

    /**
     * ファイル終端
     */
    object EndOfFile : Token()
}
