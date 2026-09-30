package com.muxy.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

class NoCommentsRule :
    Rule(
        ruleId = RuleId("muxy:no-comments"),
        about = About(maintainer = "Muxy"),
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        if (node.elementType !in commentTypes) return
        emit(node.startOffset, MESSAGE, false)
    }

    companion object {
        const val MESSAGE = "Comments aren't allowed. Make the code explain itself."

        private val commentTypes =
            setOf(ElementType.EOL_COMMENT, ElementType.BLOCK_COMMENT, ElementType.KDOC)
    }
}
