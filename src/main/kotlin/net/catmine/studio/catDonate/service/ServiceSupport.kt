package net.catmine.studio.catDonate.service

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.catmine.engine.scheduler.CatScheduler
import net.catmine.studio.catDonate.model.TransactionRecord
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.util.concurrent.CompletableFuture

interface RewardExecutor {
    fun execute(record: TransactionRecord, commands: List<String>): CompletableFuture<RewardExecution>
}

data class RewardExecution(val success: Boolean, val executed: Int, val error: String?)

class BukkitRewardExecutor(
    private val plugin: Plugin,
    private val scheduler: CatScheduler,
) : RewardExecutor {
    override fun execute(record: TransactionRecord, commands: List<String>): CompletableFuture<RewardExecution> {
        val result = CompletableFuture<RewardExecution>()
        scheduler.runGlobal {
            var executed = 0
            try {
                for (snapshot in commands) {
                    val command = snapshot.replace("%player%", record.playerName)
                    if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                        result.complete(RewardExecution(false, executed, "Console từ chối lệnh thưởng thứ ${executed + 1}"))
                        return@runGlobal
                    }
                    executed++
                }
                result.complete(RewardExecution(true, executed, null))
            } catch (throwable: Throwable) {
                plugin.logger.severe("Không thể chạy reward cho request ${record.requestId}: ${throwable.javaClass.simpleName}")
                result.complete(RewardExecution(false, executed, "Lỗi khi chạy lệnh thưởng"))
            }
        }
        return result
    }
}

interface OutcomeNotifier {
    fun notify(record: TransactionRecord)
}

object RewardSnapshotCodec {
    private val gson = Gson()
    private val type = object : TypeToken<List<String>>() {}.type
    fun encode(commands: List<String>): String = gson.toJson(commands)
    fun decode(json: String): List<String> = gson.fromJson(json, type)
}
