package com.muxy.ktlint

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.RuleSetId

class MuxyRuleSetProvider : RuleSetProviderV3(RuleSetId("muxy")) {
    override fun getRuleProviders(): Set<RuleProvider> = setOf(RuleProvider { NoCommentsRule() })
}
