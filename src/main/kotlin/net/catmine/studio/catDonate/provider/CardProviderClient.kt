package net.catmine.studio.catDonate.provider

import net.catmine.studio.catDonate.model.Telco
import java.util.concurrent.CompletableFuture

data class ProviderRequest(
    val requestId: String,
    val telco: Telco,
    val amount: Long,
    val serial: String,
    val code: String,
)

data class ProviderResponse(
    val requestId: String?,
    val status: Int,
    val declaredValue: Long?,
    val value: Long?,
    val receivedAmount: Long?,
    val transactionId: String?,
    val message: String?,
)

interface CardProviderClient {
    fun submit(request: ProviderRequest): CompletableFuture<ProviderResponse>
    fun check(request: ProviderRequest): CompletableFuture<ProviderResponse>
}

class TransientProviderException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
