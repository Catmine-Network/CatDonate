package net.catmine.studio.catDonate.config

import net.catmine.studio.catDonate.model.Telco
import org.bukkit.configuration.file.FileConfiguration
import java.net.URI
import java.math.BigDecimal
import java.time.Duration

data class ProviderSettings(
    val partnerId: String,
    val partnerKey: String,
    val sandbox: Boolean,
    val connectTimeout: Duration,
    val requestTimeout: Duration,
    internal val endpointOverride: URI? = null,
) {
    val endpoint: URI = endpointOverride ?: URI.create(
        if (sandbox) "https://sandbox.card2k.com/chargingws/v2"
        else "https://card2k.com/chargingws/v2"
    )

    val configured: Boolean get() = partnerId.isNotBlank() && partnerKey.isNotBlank()
    fun identity(): List<Any> = listOf(partnerId, partnerKey, endpoint)
}

data class DonateConfig(
    val provider: ProviderSettings,
    val cardList: Map<Telco, Set<Long>>,
    val rewards: Map<Long, RewardDefinition>,
    val rewardMultiplier: BigDecimal,
    val pollInterval: Duration,
    val maxPollAttempts: Int,
    val secretRetention: Duration,
    val submitCooldown: Duration,
    val maxPendingPerPlayer: Int,
) {
    companion object {
        fun load(config: FileConfiguration): DonateConfig {
            fun positive(path: String, default: Int): Int {
                val value = config.getInt(path, default)
                require(value >= 1) { "$path phải từ 1 trở lên" }
                return value
            }

            val cardList = Telco.entries.associateWith { telco ->
                config.getLongList("cardlist.${telco.name.lowercase()}")
                    .onEach { require(it > 0) { "Mệnh giá phải lớn hơn 0" } }
                    .toSet()
            }
            val rewardSection = config.getConfigurationSection("rewards")
            val rewards = rewardSection?.getKeys(false)?.associate { rawAmount ->
                val amount = rawAmount.toLongOrNull()
                    ?: throw IllegalArgumentException("Mệnh giá reward không hợp lệ: $rawAmount")
                val definitionSection = rewardSection.getConfigurationSection(rawAmount)
                val definition = if (definitionSection == null) {
                    RewardDefinition(null, rewardSection.getStringList(rawAmount).filter { it.isNotBlank() })
                } else {
                    val baseAmount = definitionSection.get("base-amount")?.toString()?.toBigDecimalOrNull()
                        ?: throw IllegalArgumentException("rewards.$rawAmount.base-amount không hợp lệ")
                    require(baseAmount > BigDecimal.ZERO) { "rewards.$rawAmount.base-amount phải lớn hơn 0" }
                    RewardDefinition(baseAmount, definitionSection.getStringList("commands").filter { it.isNotBlank() })
                }
                require(definition.baseAmount != null || definition.commands.none { "%reward%" in it }) {
                    "rewards.$rawAmount cần base-amount để dùng %reward%"
                }
                amount to definition
            }.orEmpty()

            val rewardMultiplier = config.get("reward-multiplier")?.toString()?.toBigDecimalOrNull()
                ?: throw IllegalArgumentException("reward-multiplier không hợp lệ")
            require(rewardMultiplier >= BigDecimal.ONE) { "reward-multiplier phải từ 1 trở lên" }
            if (rewardMultiplier.compareTo(BigDecimal.ONE) != 0) {
                val incompatible = rewards.filterValues { definition ->
                    definition.commands.isNotEmpty() &&
                        (definition.baseAmount == null || definition.commands.none { "%reward%" in it })
                }.keys
                require(incompatible.isEmpty()) {
                    "Các reward ${incompatible.sorted()} phải dùng cấu trúc base-amount/commands và placeholder %reward% khi reward-multiplier khác 1"
                }
            }

            return DonateConfig(
                provider = ProviderSettings(
                    partnerId = config.getString("card2k.partner-id", "")!!.trim(),
                    partnerKey = config.getString("card2k.partner-key", "")!!,
                    sandbox = config.getBoolean("card2k.sandbox", true),
                    connectTimeout = Duration.ofSeconds(positive("card2k.connect-timeout-seconds", 10).toLong()),
                    requestTimeout = Duration.ofSeconds(positive("card2k.request-timeout-seconds", 20).toLong()),
                ),
                cardList = cardList,
                rewards = rewards,
                rewardMultiplier = rewardMultiplier,
                pollInterval = Duration.ofSeconds(positive("polling.interval-seconds", 30).toLong()),
                maxPollAttempts = positive("polling.max-attempts", 30),
                secretRetention = Duration.ofDays(positive("polling.secret-retention-days", 7).toLong()),
                submitCooldown = Duration.ofSeconds(positive("limits.submit-cooldown-seconds", 5).toLong()),
                maxPendingPerPlayer = positive("limits.max-pending-per-player", 3),
            )
        }
    }
}

data class RewardDefinition(
    val baseAmount: BigDecimal?,
    val commands: List<String>,
) {
    fun resolve(multiplier: BigDecimal): List<String> {
        val base = baseAmount ?: return commands
        val effectiveAmount = base.multiply(multiplier).stripTrailingZeros().toPlainString()
        return commands.map { it.replace("%reward%", effectiveAmount) }
    }
}
