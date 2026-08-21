package net.catmine.studio.catDonate.security

import net.catmine.studio.catDonate.model.Telco
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CardSecretsTest {
    private val secrets = CardSecrets.forTesting(ByteArray(32) { it.toByte() }, ByteArray(32) { (it + 7).toByte() })

    @Test
    fun `aes gcm round trips and uses random nonce`() {
        val first = secrets.encrypt("AbCd123456")
        val second = secrets.encrypt("AbCd123456")
        assertNotEquals(first, second)
        assertEquals("AbCd123456", secrets.decrypt(first))
        assertEquals("AbCd123456", secrets.decrypt(second))
    }

    @Test
    fun `fingerprint is deterministic and binds all card fields`() {
        val first = secrets.fingerprint("VIETTEL", "SERIAL01", "CODE001")
        assertEquals(first, secrets.fingerprint("viettel", "SERIAL01", "CODE001"))
        assertNotEquals(first, secrets.fingerprint("VIETTEL", "SERIAL02", "CODE001"))
        assertNotEquals(first, secrets.fingerprint("VIETTEL", "SERIAL01", "CODE002"))
    }

    @Test
    fun `masks serial without exposing middle`() {
        assertEquals("SE****01", CardSecrets.maskSerial("SERIAL01"))
        assertEquals("****", CardSecrets.maskSerial("1234"))
    }

    @Test
    fun `telco aliases are accepted`() {
        assertEquals(Telco.VINAPHONE, Telco.parse("vina"))
        assertEquals(Telco.MOBIFONE, Telco.parse("mobi"))
        assertEquals(Telco.GARENA, Telco.parse("GARENA"))
        assertEquals(Telco.ZING, Telco.parse("zing"))
        assertEquals(Telco.VCOIN, Telco.parse("v-coin"))
        assertNull(Telco.parse("unknown"))
    }
}
