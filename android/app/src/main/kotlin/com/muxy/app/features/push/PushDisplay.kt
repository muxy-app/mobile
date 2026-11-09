package com.muxy.app.features.push

enum class PushKind(
    val wireName: String,
    val fallbackBody: String,
) {
    ATTENTION("attention", "An agent needs your attention"),
    COMPLETED("completed", "An agent finished working"),
    ;

    companion object {
        const val FALLBACK_TITLE = "Muxy"

        fun from(wireName: String?): PushKind = entries.firstOrNull { it.wireName == wireName } ?: ATTENTION
    }
}

data class PushAlert(
    val title: String,
    val body: String,
    val subText: String? = null,
)

data class PushNotification(
    val alert: PushAlert,
    val kind: PushKind,
    val tag: String?,
    val group: String?,
    val target: PushTarget?,
) {
    companion object {
        fun from(
            data: Map<String, String>,
            keys: List<PushKey>,
            serverNames: Map<String, String>,
        ): PushNotification? {
            if (data[VERSION_FIELD] != SUPPORTED_VERSION) return null
            val kind = PushKind.from(data[KIND_FIELD])
            val message = data[PAYLOAD_FIELD]?.let { PushOpener.open(it, keys) }
            return PushNotification(
                alert = alert(message, kind, data, serverNames),
                kind = kind,
                tag = data[COLLAPSE_FIELD],
                group = data[THREAD_FIELD],
                target = message?.target,
            )
        }

        private fun alert(
            message: PushMessage?,
            kind: PushKind,
            data: Map<String, String>,
            serverNames: Map<String, String>,
        ): PushAlert {
            if (message == null) {
                return PushAlert(
                    title = data[TITLE_FIELD] ?: PushKind.FALLBACK_TITLE,
                    body = data[BODY_FIELD] ?: kind.fallbackBody,
                )
            }
            val subText = serverNames[message.serverId].takeIf { serverNames.size > 1 }
            return PushAlert(message.title, message.body, subText)
        }

        private const val VERSION_FIELD = "muxy_v"
        private const val SUPPORTED_VERSION = "1"
        private const val KIND_FIELD = "kind"
        private const val PAYLOAD_FIELD = "payload"
        private const val TITLE_FIELD = "title"
        private const val BODY_FIELD = "body"
        private const val COLLAPSE_FIELD = "collapse_id"
        private const val THREAD_FIELD = "thread_id"
    }
}
