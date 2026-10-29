package com.muxy.app.models

enum class TabTileKind {
    AGENT,
    SHELL,
    GIT,
    OTHER,
    ;

    companion object {
        fun of(kind: TabKind): TabTileKind =
            when (kind) {
                TabKind.Terminal -> SHELL
                TabKind.Vcs -> GIT
                is TabKind.Unsupported -> OTHER
            }
    }
}
