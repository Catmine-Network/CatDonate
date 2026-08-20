package net.catmine.studio.catDonate.ui

import net.catmine.studio.catDonate.model.Telco
import net.catmine.studio.catDonate.service.CardTopUpService
import org.bukkit.entity.Player

fun interface TopUpUi {
    fun open(player: Player)
}

interface BedrockTopUpUi : TopUpUi {
    fun isBedrockPlayer(player: Player): Boolean
}

class TopUpUiRouter(
    private val javaUi: TopUpUi,
    private val bedrockUi: BedrockTopUpUi?,
) : TopUpUi {
    override fun open(player: Player) {
        if (bedrockUi?.isBedrockPlayer(player) == true) bedrockUi.open(player)
        else javaUi.open(player)
    }
}

data class CardChoice(
    val telco: Telco,
    val amount: Long,
) {
    companion object {
        fun formatAmount(amount: Long): String = amount.toString()
            .reversed()
            .chunked(3)
            .joinToString(".")
            .reversed()
            .plus("đ")
    }
}

data class CardCatalog(
    val telcos: List<Telco>,
    val amounts: List<Long>,
) {
    companion object {
        fun from(service: CardTopUpService): CardCatalog = from(service.cardOptions())

        internal fun from(options: Map<Telco, Set<Long>>): CardCatalog = CardCatalog(
            telcos = Telco.entries.filter { options[it].orEmpty().isNotEmpty() },
            amounts = options.values.asSequence().flatten().distinct().sorted().toList(),
        )
    }
}
