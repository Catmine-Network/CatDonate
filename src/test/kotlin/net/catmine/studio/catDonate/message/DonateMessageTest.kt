package net.catmine.studio.catDonate.message

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DonateMessageTest {
    @Test
    fun `bundled defaults use CatEngine brace placeholders`() {
        val placeholderNames = setOf(
            "request_id", "amount", "telco", "seconds", "limit", "reason",
            "status", "created_at", "player", "reward_state", "polls", "error", "action",
        )

        DonateMessage.entries.forEach { message ->
            placeholderNames.forEach { name ->
                assertFalse(message.defaultText.contains("<$name>"), "${message.path} still uses <$name>")
            }
        }
    }

    @Test
    fun `legacy angle placeholders remain compatible with existing messages file`() {
        val rendered = replaceLegacyPlaceholders(
            "<green>Mã giao dịch: <request_id>, giá trị <amount>.</green>",
            mapOf("request_id" to "123456", "amount" to "10000"),
        )

        assertEquals("<green>Mã giao dịch: 123456, giá trị 10000.</green>", rendered)
    }
}
