package net.catmine.studio.catDonate.service

import net.catmine.studio.catDonate.config.DonateConfig
import net.catmine.studio.catDonate.model.AdminAction
import net.catmine.studio.catDonate.model.CardSubmission
import net.catmine.studio.catDonate.model.SubmissionResult
import net.catmine.studio.catDonate.model.Telco
import net.catmine.studio.catDonate.model.TransactionRecord
import java.util.UUID
import java.util.concurrent.CompletableFuture

interface CardTopUpService {
    fun cardOptions(): Map<Telco, Set<Long>>
    fun submit(submission: CardSubmission): CompletableFuture<SubmissionResult>
    fun startProcessing(requestId: String)
    fun findStatus(playerId: UUID, requestId: String? = null): CompletableFuture<TransactionRecord?>
    fun findAdmin(requestId: String): CompletableFuture<TransactionRecord?>
    fun recentSuccessfulTransactions(limit: Int): CompletableFuture<List<TransactionRecord>>
    fun adminAction(requestId: String, action: AdminAction, actor: String): CompletableFuture<Boolean>
    fun reload(config: DonateConfig): CompletableFuture<Boolean>
}
