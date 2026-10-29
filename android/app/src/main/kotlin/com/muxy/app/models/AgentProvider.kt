package com.muxy.app.models

enum class AgentProvider(
    val displayName: String,
) {
    CLAUDE("Claude Code"),
    CODEX("Codex"),
    OPEN_CODE("OpenCode"),
    CURSOR("Cursor"),
    COPILOT("Copilot"),
    DROID("Droid"),
    PI("Pi"),
    GROK("Grok"),
    KIRO("Kiro"),
    XAL("Xal"),
    ANTIGRAVITY("Antigravity"),
    OH_MY_PI("Oh My Pi"),
    ;

    companion object {
        private val aliases =
            mapOf(
                "claude" to CLAUDE,
                "claudecode" to CLAUDE,
                "codex" to CODEX,
                "opencode" to OPEN_CODE,
                "cursor" to CURSOR,
                "copilot" to COPILOT,
                "githubcopilot" to COPILOT,
                "droid" to DROID,
                "factory" to DROID,
                "pi" to PI,
                "grok" to GROK,
                "kiro" to KIRO,
                "xal" to XAL,
                "antigravity" to ANTIGRAVITY,
                "ohmypi" to OH_MY_PI,
                "omp" to OH_MY_PI,
            )

        fun fromName(name: String): AgentProvider? = aliases[name.lowercase().filter(Char::isLetterOrDigit)]
    }
}
