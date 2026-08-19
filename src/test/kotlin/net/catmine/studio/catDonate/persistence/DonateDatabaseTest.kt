package net.catmine.studio.catDonate.persistence

import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path

class DonateDatabaseTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `catengine database manager creates sqlite file and runs flyway`() {
        val plugin = Proxy.newProxyInstance(
            Plugin::class.java.classLoader,
            arrayOf(Plugin::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getDataFolder" -> temp.toFile()
                "getName" -> "CatDonateTest"
                "isEnabled" -> true
                else -> defaultValue(method.returnType)
            }
        } as Plugin

        DonateDatabase(plugin).use { database ->
            database.connect()
            assertEqualsZero(database.repository.countActive())
        }
        assertTrue(Files.exists(temp.resolve("transactions.db")))
    }

    private fun assertEqualsZero(value: Int) = org.junit.jupiter.api.Assertions.assertEquals(0, value)

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        Boolean::class.javaPrimitiveType -> false
        Byte::class.javaPrimitiveType -> 0.toByte()
        Short::class.javaPrimitiveType -> 0.toShort()
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        Char::class.javaPrimitiveType -> '\u0000'
        else -> null
    }
}
