package jp.skypencil.kosmo.backend.sql

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class TokenizerSpec :
    DescribeSpec({
        describe("Tokenizer") {
            val tokenizer = Tokenizer()

            it("can tokenize simple SELECT statement") {
                val sql = "SELECT * FROM table1;"
                val tokens = tokenizer.tokenize(sql)

                tokens shouldBe
                    listOf(
                        Token.Keyword("SELECT"),
                        Token.Symbol("*"),
                        Token.Keyword("FROM"),
                        Token.Identifier("table1"),
                        Token.Symbol(";"),
                        Token.EndOfFile,
                    )
            }

            it("can tokenize SELECT with column names") {
                val sql = "SELECT id, name FROM users"
                val tokens = tokenizer.tokenize(sql)

                tokens shouldBe
                    listOf(
                        Token.Keyword("SELECT"),
                        Token.Identifier("id"),
                        Token.Symbol(","),
                        Token.Identifier("name"),
                        Token.Keyword("FROM"),
                        Token.Identifier("users"),
                        Token.EndOfFile,
                    )
            }

            it("can tokenize WHERE clause with string literal") {
                val sql = "SELECT * FROM users WHERE name = 'John'"
                val tokens = tokenizer.tokenize(sql)

                tokens shouldBe
                    listOf(
                        Token.Keyword("SELECT"),
                        Token.Symbol("*"),
                        Token.Keyword("FROM"),
                        Token.Identifier("users"),
                        Token.Keyword("WHERE"),
                        Token.Identifier("name"),
                        Token.Symbol("="),
                        Token.StringLiteral("John"),
                        Token.EndOfFile,
                    )
            }

            it("can tokenize WHERE clause with number literal") {
                val sql = "SELECT * FROM users WHERE age > 25"
                val tokens = tokenizer.tokenize(sql)

                tokens shouldBe
                    listOf(
                        Token.Keyword("SELECT"),
                        Token.Symbol("*"),
                        Token.Keyword("FROM"),
                        Token.Identifier("users"),
                        Token.Keyword("WHERE"),
                        Token.Identifier("age"),
                        Token.Symbol(">"),
                        Token.NumberLiteral("25"),
                        Token.EndOfFile,
                    )
            }

            it("can tokenize INSERT statement") {
                val sql = "INSERT INTO users (id, name) VALUES (1, 'Alice')"
                val tokens = tokenizer.tokenize(sql)

                tokens shouldBe
                    listOf(
                        Token.Keyword("INSERT"),
                        Token.Keyword("INTO"),
                        Token.Identifier("users"),
                        Token.Symbol("("),
                        Token.Identifier("id"),
                        Token.Symbol(","),
                        Token.Identifier("name"),
                        Token.Symbol(")"),
                        Token.Keyword("VALUES"),
                        Token.Symbol("("),
                        Token.NumberLiteral("1"),
                        Token.Symbol(","),
                        Token.StringLiteral("Alice"),
                        Token.Symbol(")"),
                        Token.EndOfFile,
                    )
            }

            it("can handle decimal numbers") {
                val sql = "SELECT price FROM products WHERE price > 99.99"
                val tokens = tokenizer.tokenize(sql)

                tokens shouldBe
                    listOf(
                        Token.Keyword("SELECT"),
                        Token.Identifier("price"),
                        Token.Keyword("FROM"),
                        Token.Identifier("products"),
                        Token.Keyword("WHERE"),
                        Token.Identifier("price"),
                        Token.Symbol(">"),
                        Token.NumberLiteral("99.99"),
                        Token.EndOfFile,
                    )
            }

            it("can handle identifiers with underscores") {
                val sql = "SELECT user_id FROM user_table"
                val tokens = tokenizer.tokenize(sql)

                tokens shouldBe
                    listOf(
                        Token.Keyword("SELECT"),
                        Token.Identifier("user_id"),
                        Token.Keyword("FROM"),
                        Token.Identifier("user_table"),
                        Token.EndOfFile,
                    )
            }

            it("recognizes keywords in any case") {
                val sql = "select * from Table1 where id = 1"
                val tokens = tokenizer.tokenize(sql)

                tokens shouldBe
                    listOf(
                        Token.Keyword("SELECT"),
                        Token.Symbol("*"),
                        Token.Keyword("FROM"),
                        Token.Identifier("Table1"),
                        Token.Keyword("WHERE"),
                        Token.Identifier("id"),
                        Token.Symbol("="),
                        Token.NumberLiteral("1"),
                        Token.EndOfFile,
                    )
            }

            it("can handle empty input") {
                val sql = ""
                val tokens = tokenizer.tokenize(sql)

                tokens shouldBe listOf(Token.EndOfFile)
            }

            it("can handle whitespace-only input") {
                val sql = "   \t\n  "
                val tokens = tokenizer.tokenize(sql)

                tokens shouldBe listOf(Token.EndOfFile)
            }
        }
    })
