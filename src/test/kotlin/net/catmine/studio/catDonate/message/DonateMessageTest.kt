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
            "initial_seconds", "max_minutes", "reward_status", "next_check", "detail",
            "next_check_at", "serial", "code",
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

    @Test
    fun `player facing defaults do not expose transaction or provider details`() {
        val playerMessages = listOf(
            DonateMessage.USAGE_SUBMIT,
            DonateMessage.NOT_CONFIGURED,
            DonateMessage.DUPLICATE_CARD,
            DonateMessage.DUPLICATE_CARD_UNKNOWN,
            DonateMessage.SUBMITTED,
            DonateMessage.SUBMITTED_WAIT,
            DonateMessage.PENDING,
            DonateMessage.SUCCESS,
            DonateMessage.SUCCESS_NO_REWARD,
            DonateMessage.WRONG_VALUE,
            DonateMessage.REWARD_FAILED,
            DonateMessage.REWARD_CONFIRMED,
            DonateMessage.FAILED,
            DonateMessage.MAINTENANCE,
            DonateMessage.TIMEOUT,
            DonateMessage.POLL_EXHAUSTED,
            DonateMessage.NEEDS_REVIEW,
            DonateMessage.STATUS,
            DonateMessage.INTERNAL_ERROR,
        )

        playerMessages.forEach { message ->
            assertFalse(message.defaultText.contains("{request_id}"), "${message.path} exposes request_id")
            assertFalse(message.defaultText.contains("Card2K", ignoreCase = true), "${message.path} exposes provider")
        }
    }
}
