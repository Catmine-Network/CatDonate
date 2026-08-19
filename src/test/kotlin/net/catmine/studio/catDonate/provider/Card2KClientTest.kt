package net.catmine.studio.catDonate.provider

import com.sun.net.httpserver.HttpServer
import net.catmine.studio.catDonate.config.ProviderSettings
import net.catmine.studio.catDonate.model.Telco
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeUnit

class Card2KClientTest {
    private var server: HttpServer? = null

    @AfterEach
    fun stopServer() {
        server?.stop(0)
    }

    @Test
    fun `creates exact md5 signature and url encoding`() {
        assertEquals("2540ecd070cece3a665973e457504924", Card2KClient.signature("secret", "ABC123", "SER456"))
        assertEquals("code=A%2BB+C&serial=S%26R", Card2KClient.formEncode(linkedMapOf("code" to "A+B C", "serial" to "S&R")))
    }

    @Test
    fun `parses string and numeric response fields`() {
        val response = Card2KClient.parseResponse(
            """{"request_id":123456,"status":"2","declared_value":"50000","value":20000,"amount":"18000","trans_id":99}"""
        )
        assertEquals("123456", response.requestId)
        assertEquals(2, response.status)
        assertEquals(50_000, response.declaredValue)
        assertEquals(20_000, response.value)
        assertEquals(18_000, response.receivedAmount)
        assertEquals("99", response.transactionId)
    }

    @Test
    fun `posts charging then check to same endpoint`() {
        val commands = mutableListOf<Map<String, String>>()
        val acceptHeaders = mutableListOf<String?>()
        var call = 0
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/chargingws/v2") { exchange ->
                acceptHeaders += exchange.requestHeaders.getFirst("Accept")
                commands += decodeForm(exchange.requestBody.readAllBytes().toString(StandardCharsets.UTF_8))
                val status = if (call++ == 0) 99 else 1
                val response = """{"request_id":"1700000000000123","status":$status,"amount":"10000","value":"10000"}"""
                exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
                exchange.responseBody.use { it.write(response.toByteArray()) }
            }
            start()
        }
        val endpoint = URI.create("http://127.0.0.1:${server!!.address.port}/chargingws/v2")
        val client = Card2KClient(ProviderSettings("partner", "key", true, Duration.ofSeconds(2), Duration.ofSeconds(2), endpoint))
        val request = ProviderRequest("1700000000000123", Telco.VIETTEL, 10_000, "SERIAL01", "CODE001")

        assertEquals(99, client.submit(request).get(3, TimeUnit.SECONDS).status)
        assertEquals(1, client.check(request).get(3, TimeUnit.SECONDS).status)
        assertEquals(listOf("charging", "check"), commands.map { it["command"] })
        assertEquals(listOf("application/json", "application/json"), acceptHeaders)
        assertTrue(commands.all { it["request_id"] == request.requestId && it["partner_id"] == "partner" })
        assertTrue(commands.all { it["sign"] == Card2KClient.signature("key", "CODE001", "SERIAL01") })
    }

    private fun decodeForm(raw: String): Map<String, String> = raw.split('&').associate { pair ->
        val (key, value) = pair.split('=', limit = 2)
        URLDecoder.decode(key, StandardCharsets.UTF_8) to URLDecoder.decode(value, StandardCharsets.UTF_8)
    }
}
