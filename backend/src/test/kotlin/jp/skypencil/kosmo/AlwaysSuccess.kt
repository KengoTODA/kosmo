package jp.skypencil.kosmo

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull

class AlwaysSuccess :
    DescribeSpec({
        describe("kosmo") {
            it("uses kotest") {
                this.shouldNotBeNull()
            }
        }
    })
