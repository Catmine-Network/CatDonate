package net.catmine.studio.catDonate.config

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration

class DonateConfigTest {
    @Test
    fun `decimal multiplier scales reward placeholder exactly`() {
        val config = load(
            """
            rewards:
              "10000":
                base-amount: 10
                commands: ["points give %player% %reward%"]
            reward-multiplier: 1.2
            """.trimIndent(),
        )

        assertEquals(BigDecimal("1.2"), config.rewardMultiplier)
        assertEquals(listOf("points give %player% 12"), config.rewards.getValue(10_000).resolve(config.rewardMultiplier))
    }

    @Test
    fun `legacy command list is rejected when decimal multiplier would be ambiguous`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            load(
                """
                rewards:
                  "10000": ["points give %player% 10"]
                reward-multiplier: 1.2
                """.trimIndent(),
            )
        }

        kotlin.test.assertContains(error.message.orEmpty(), "base-amount/commands")
    }

    @Test
    fun `polling defaults wait 15 seconds then increase by 5 seconds`() {
        val config = load("reward-multiplier: 1")

        assertEquals(Duration.ofSeconds(15), config.pollInterval)
        assertEquals(Duration.ofSeconds(5), config.pollIntervalIncrement)
    }

    private fun load(raw: String): DonateConfig = DonateConfig.load(YamlConfiguration().apply { loadFromString(raw) })
}
