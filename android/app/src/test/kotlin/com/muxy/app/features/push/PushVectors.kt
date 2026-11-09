package com.muxy.app.features.push

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object PushVectors {
    val keyBytes = ByteArray(32) { it.toByte() }
    val nonce = ByteArray(12) { (0x20 + it).toByte() }
    const val SERVER_ID = "6b0c7e2a9f1d4c3b8a7e6d5c4b3a2910"
    const val PROJECT_ID = "0f3a5c7e9b1d2f4a6c8e0b2d4f6a8c1e"
    const val PLAINTEXT =
        "{\"v\":1,\"server_id\":\"6b0c7e2a9f1d4c3b8a7e6d5c4b3a2910\",\"project_id\":\"0f3a5c7e9b1d2f4a6c8e0b2d4f6a8c1e\"," +
            "\"session_id\":42,\"event_id\":1731067200000123,\"kind\":\"attention\",\"title\":\"Claude Code · muxy-website\"," +
            "\"body\":\"Needs your attention\",\"ts\":1731067200}"
    const val PAYLOAD =
        "AWJ9-ET_FPTtICEiIyQlJicoKSorqRjQUlapNixpGTC4pGqrkLRr1r6x4lONW5Nec3Dsb21B7dZmtkQCvyKZW_Fsayyp0BPdvrgQDxG8DRV4L8ZV" +
            "tLwE9ymm8db3vgneQoI_CAXn8QdlgZi-e_8t8E4ueqi9OjcfM-povICs79duzJiSehSztXaj8o1oJpdpe6ru5goG1UqtCt41hle_klo-Dy2U5OYJ" +
            "Xrr2vqgKKtxb_ra_ubCaUg3w0QPlldusnpwIWMfKxtjhaYCgWQ7gLBPGV6bSmo4Bcxb1rSswP5mcjbzPte8f11CjunfsIcZmKTV0KnlIcQD_9AAR3" +
            "n9Gr-LLzKEm-U6CvwQ4NQPD2UDfWA59eAGjg59TxG-3fQ"
    val target = PushTarget(SERVER_ID, 42u, PROJECT_ID, 1_731_067_200_000_123u)

    val key: PushKey
        get() = checkNotNull(PushKey.of(SERVER_ID, keyBytes))

    fun seal(
        plaintext: String,
        keyBytes: ByteArray = this.keyBytes,
        serverId: String = SERVER_ID,
    ): String {
        val header = byteArrayOf(1) + checkNotNull(PushKey.of(serverId, keyBytes)).keyId
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(header + serverId.toByteArray(Charsets.UTF_8))
        val sealed = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(header + nonce + sealed)
    }

    fun message(
        serverId: String = SERVER_ID,
        version: Int = 1,
    ): String =
        "{\"v\":$version,\"server_id\":\"$serverId\",\"project_id\":\"$PROJECT_ID\",\"session_id\":42,\"event_id\":7," +
            "\"kind\":\"completed\",\"title\":\"Codex · muxy\",\"body\":\"Finished working\",\"ts\":1731067200,\"extra\":\"ignored\"}"

    fun data(
        payload: String = PAYLOAD,
        kind: String = "attention",
    ): Map<String, String> =
        mapOf(
            "muxy_v" to "1",
            "kind" to kind,
            "payload" to payload,
            "title" to "Muxy",
            "body" to "An agent needs your attention",
            "collapse_id" to "usZ3J1QStv6L6omf",
            "thread_id" to "aJnt975IClwhFwRP",
        )
}
