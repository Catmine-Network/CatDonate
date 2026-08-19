package net.catmine.studio.catDonate.provider

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.catmine.studio.catDonate.config.ProviderSettings
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.CompletableFuture

class Card2KClient(
    private val settings: ProviderSettings,
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(settings.connectTimeout)
        .build(),
) : CardProviderClient {
    override fun submit(request: ProviderRequest): CompletableFuture<ProviderResponse> = execute("charging", request)
    override fun check(request: ProviderRequest): CompletableFuture<ProviderResponse> = execute("check", request)

    private fun execute(command: String, request: ProviderRequest): CompletableFuture<ProviderResponse> {
        val fields = linkedMapOf(
            "telco" to request.telco.apiName,
            "code" to request.code,
            "serial" to request.serial,
            "amount" to request.amount.toString(),
            "request_id" to request.requestId,
            "partner_id" to settings.partnerId,
            "sign" to signature(settings.partnerKey, request.code, request.serial),
            "command" to command,
        )
        val httpRequest = HttpRequest.newBuilder(settings.endpoint)
            .timeout(settings.requestTimeout)
            .header("Accept", "application/json")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formEncode(fields)))
            .build()
        return client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .handle { response, failure ->
                if (failure != null) throw TransientProviderException("Không thể kết nối Card2K", failure)
                if (response.statusCode() !in 200..299) {
                    throw TransientProviderException("Card2K HTTP ${response.statusCode()}")
                }
                try {
                    parseResponse(response.body())
                } catch (exception: Exception) {
                    throw TransientProviderException("Phản hồi JSON không hợp lệ", exception)
                }
            }
    }

    companion object {
        fun signature(partnerKey: String, code: String, serial: String): String =
            MessageDigest.getInstance("MD5")
                .digest((partnerKey + code + serial).toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        fun formEncode(fields: Map<String, String>): String = fields.entries.joinToString("&") {
            "${encode(it.key)}=${encode(it.value)}"
        }

        private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

        fun parseResponse(raw: String): ProviderResponse {
            val root = JsonParser.parseString(raw).asJsonObject
            return ProviderResponse(
                requestId = root.text("request_id"),
                status = root.long("status")?.toInt() ?: error("Thiếu status"),
                declaredValue = root.long("declared_value"),
                value = root.long("value"),
                receivedAmount = root.long("amount")
                    ?: root.long("amount_received")
                    ?: root.long("received_amount"),
                transactionId = root.text("trans_id") ?: root.text("transaction_id"),
                message = root.text("message"),
            )
        }

        private fun JsonObject.text(name: String): String? = get(name)?.takeUnless(JsonElement::isJsonNull)?.asString
        private fun JsonObject.long(name: String): Long? {
            val element = get(name)?.takeUnless(JsonElement::isJsonNull) ?: return null
            return element.asString.trim().toLongOrNull()
        }
    }
}
