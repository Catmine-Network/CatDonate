package net.catmine.studio.catDonate.ui

import net.catmine.studio.catDonate.model.Telco
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CardChoiceTest {
    @Test
    fun `catalog separates enabled telcos and denomination union in stable order`() {
        val catalog = CardCatalog.from(
            mapOf(
                Telco.VINAPHONE to setOf(50_000L, 10_000L),
                Telco.VIETTEL to setOf(20_000L),
                Telco.GARENA to emptySet(),
            ),
        )

        assertEquals(
            listOf(Telco.VIETTEL, Telco.VINAPHONE),
            catalog.telcos,
        )
        assertEquals(
            listOf(10_000L, 20_000L, 50_000L),
            catalog.amounts,
        )
        assertEquals("50.000đ", CardChoice.formatAmount(50_000L))
    }
}
