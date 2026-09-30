package com.muxy.ktlint

import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.api.LintError
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class NoCommentsRuleTest {
    private val engine = KtLintRuleEngine(ruleProviders = setOf(RuleProvider { NoCommentsRule() }))

    @Test
    fun acceptsCodeWithoutComments() {
        assertEquals(emptyList<LintError>(), errors("fun answer() = 42\n"))
    }

    @Test
    fun reportsLineComments() {
        assertReported(line = 2, code = "fun answer() = 42\n// the answer\n")
    }

    @Test
    fun reportsTrailingLineComments() {
        assertReported(line = 1, code = "fun answer() = 42 // the answer\n")
    }

    @Test
    fun reportsBlockComments() {
        assertReported(line = 1, code = "fun answer() = /* the answer */ 42\n")
    }

    @Test
    fun reportsKDoc() {
        assertReported(line = 1, code = "/** The answer. */\nfun answer() = 42\n")
    }

    @Test
    fun reportsCommentsInScripts() {
        assertReported(line = 2, code = "val answer = 42\n// the answer\n", script = true)
    }

    @Test
    fun ignoresCommentMarkersInsideStrings() {
        assertEquals(emptyList<LintError>(), errors("val url = \"https://muxy.app/* // */\"\n"))
    }

    private fun assertReported(
        line: Int,
        code: String,
        script: Boolean = false,
    ) {
        val error = errors(code, script).single()
        assertEquals("muxy:no-comments", error.ruleId.value)
        assertEquals(line, error.line)
        assertEquals(NoCommentsRule.MESSAGE, error.detail)
    }

    private fun errors(
        code: String,
        script: Boolean = false,
    ): List<LintError> {
        val errors = mutableListOf<LintError>()
        engine.lint(Code.fromSnippet(code, script = script)) { errors += it }
        return errors
    }
}
