package jp.skypencil.kosmo.backend.sql

/**
 * SQL文字列をトークン列に分解するトークナイザー。
 * 最小限の実装で、将来の拡張性を考慮している。
 */
class Tokenizer {
    private val keywords =
        setOf(
            "SELECT",
            "FROM",
            "WHERE",
            "INSERT",
            "UPDATE",
            "DELETE",
            "CREATE",
            "DROP",
            "TABLE",
            "INDEX",
            "INTO",
            "VALUES",
            "SET",
            "AND",
            "OR",
            "NOT",
            "NULL",
            "IS",
            "AS",
        )

    private val symbols =
        setOf(
            ',',
            ';',
            '(',
            ')',
            '*',
            '=',
            '<',
            '>',
            '!',
            '+',
            '-',
            '/',
            '%',
        )

    /**
     * SQL文字列をトークン列に分解する。
     *
     * @param sql 分解対象のSQL文字列
     * @return トークンのリスト（ホワイトスペースは除外）
     */
    fun tokenize(sql: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var current = 0

        while (current < sql.length) {
            val char = sql[current]

            when {
                char.isWhitespace() -> {
                    current++
                }
                char == '\'' -> {
                    val (stringLiteral, newPos) = parseStringLiteral(sql, current)
                    tokens.add(stringLiteral)
                    current = newPos
                }
                char.isDigit() -> {
                    val (numberLiteral, newPos) = parseNumberLiteral(sql, current)
                    tokens.add(numberLiteral)
                    current = newPos
                }
                char.isLetter() || char == '_' -> {
                    val (identifier, newPos) = parseIdentifier(sql, current)
                    tokens.add(identifier)
                    current = newPos
                }
                char in symbols -> {
                    tokens.add(Token.Symbol(char.toString()))
                    current++
                }
                else -> {
                    // 未知の文字は記号として扱う
                    tokens.add(Token.Symbol(char.toString()))
                    current++
                }
            }
        }

        tokens.add(Token.EndOfFile)
        return tokens
    }

    private fun parseStringLiteral(
        sql: String,
        start: Int,
    ): Pair<Token.StringLiteral, Int> {
        require(sql[start] == '\'') { "String literal must start with single quote" }

        var current = start + 1
        val content = StringBuilder()

        while (current < sql.length && sql[current] != '\'') {
            if (sql[current] == '\\' && current + 1 < sql.length) {
                // エスケープシーケンスの簡易処理
                current++
                content.append(sql[current])
            } else {
                content.append(sql[current])
            }
            current++
        }

        if (current >= sql.length) {
            throw IllegalArgumentException("Unterminated string literal")
        }

        current++ // 終端の'をスキップ
        return Pair(Token.StringLiteral(content.toString()), current)
    }

    private fun parseNumberLiteral(
        sql: String,
        start: Int,
    ): Pair<Token.NumberLiteral, Int> {
        var current = start
        val number = StringBuilder()

        while (current < sql.length && (sql[current].isDigit() || sql[current] == '.')) {
            number.append(sql[current])
            current++
        }

        return Pair(Token.NumberLiteral(number.toString()), current)
    }

    private fun parseIdentifier(
        sql: String,
        start: Int,
    ): Pair<Token, Int> {
        var current = start
        val identifier = StringBuilder()

        while (current < sql.length && (sql[current].isLetterOrDigit() || sql[current] == '_')) {
            identifier.append(sql[current])
            current++
        }

        val value = identifier.toString()
        val token =
            if (value.uppercase() in keywords) {
                Token.Keyword(value.uppercase())
            } else {
                Token.Identifier(value)
            }

        return Pair(token, current)
    }
}
