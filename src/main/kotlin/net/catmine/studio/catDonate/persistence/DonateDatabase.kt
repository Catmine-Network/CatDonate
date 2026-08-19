package net.catmine.studio.catDonate.persistence

import net.catmine.engine.database.DatabaseConfig
import net.catmine.engine.database.DatabaseManager
import net.catmine.engine.database.DatabaseType
import org.bukkit.plugin.Plugin

class DonateDatabase(plugin: Plugin) : AutoCloseable {
    private val manager = DatabaseManager(
        DatabaseConfig(
            type = DatabaseType.SQLITE,
            database = "catdonate",
            maximumPoolSize = 1,
            minimumIdle = 1,
            connectionTimeoutMs = 20_000,
            poolName = "CatDonate-SQLite",
            migrationLocations = listOf("classpath:db/migration"),
            sqliteFile = plugin.dataFolder.resolve("transactions.db").absolutePath,
            sqliteJournalMode = "WAL",
            sqliteSynchronous = "NORMAL",
            sqliteBusyTimeoutMs = 10_000,
            sqliteForeignKeys = true,
        ),
        plugin.javaClass.classLoader,
    )

    lateinit var repository: TransactionRepository
        private set

    fun connect() {
        manager.connect()
        // DatabaseManager initializes both Hikari and Exposed; repository owns the JDBC persistence boundary.
        manager.database()
        repository = TransactionRepository(manager.dataSource())
    }

    override fun close() = manager.close()
}
