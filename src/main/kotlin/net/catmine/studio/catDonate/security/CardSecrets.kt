package net.catmine.studio.catDonate.security

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CardSecrets private constructor(
    private val encryptionKey: SecretKey,
    private val fingerprintKey: SecretKey,
    private val random: SecureRandom = SecureRandom(),
) {
    fun encrypt(value: String): String {
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, GCMParameterSpec(128, nonce))
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(nonce + encrypted)
    }

    fun decrypt(value: String): String {
        val payload = Base64.getDecoder().decode(value)
        require(payload.size > 28) { "Ciphertext không hợp lệ" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, GCMParameterSpec(128, payload.copyOfRange(0, 12)))
        return String(cipher.doFinal(payload.copyOfRange(12, payload.size)), StandardCharsets.UTF_8)
    }

    fun fingerprint(telco: String, serial: String, code: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(fingerprintKey)
        val canonical = "${telco.uppercase()}\u0000$serial\u0000$code"
        return mac.doFinal(canonical.toByteArray(StandardCharsets.UTF_8)).toHex()
    }

    companion object {
        fun loadOrCreate(dataFolder: Path): CardSecrets {
            Files.createDirectories(dataFolder)
            val path = dataFolder.resolve("secrets.yml")
            val yaml = YamlConfiguration.loadConfiguration(path.toFile())
            var changed = false
            fun key(pathName: String): ByteArray {
                val existing = yaml.getString(pathName)
                if (existing != null) {
                    val bytes = Base64.getDecoder().decode(existing)
                    require(bytes.size == 32) { "$pathName phải là khóa 256-bit" }
                    return bytes
                }
                changed = true
                return ByteArray(32).also(SecureRandom()::nextBytes).also {
                    yaml.set(pathName, Base64.getEncoder().encodeToString(it))
                }
            }
            val encryption = key("encryption-key")
            val fingerprint = key("fingerprint-key")
            if (changed) yaml.save(path.toFile())
            runCatching {
                Files.setPosixFilePermissions(path, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
            }
            return CardSecrets(SecretKeySpec(encryption, "AES"), SecretKeySpec(fingerprint, "HmacSHA256"))
        }

        fun forTesting(encryption: ByteArray, fingerprint: ByteArray): CardSecrets = CardSecrets(
            SecretKeySpec(encryption, "AES"), SecretKeySpec(fingerprint, "HmacSHA256")
        )

        fun maskSerial(serial: String): String = when {
            serial.length <= 4 -> "*".repeat(serial.length)
            else -> serial.take(2) + "*".repeat(serial.length - 4) + serial.takeLast(2)
        }
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
