package com.semmyx.ledger.data.security

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.semmyx.ledger.domain.PinPolicy
import com.semmyx.ledger.domain.PinResult
import com.semmyx.ledger.domain.SecurityRepository
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.json.JSONObject

/** PIN plaintext is never persisted. A slow salted verifier is additionally Keystore-encrypted. */
class KeystoreSecurityRepository(
    context: Context,
    private val vault: KeystoreVault,
    private val clock: Clock,
) : SecurityRepository {
    private val resolver = context.applicationContext.contentResolver
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()
    private fun bootCount() = Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1)

    override suspend fun hasPin(): Boolean = vault.read { json ->
        val salt = json.has("pinSalt")
        val hash = json.has("pinHash")
        check(salt == hash) { "Incomplete PIN verifier" }
        salt
    }

    override suspend fun createPin(pin: CharArray) {
        try {
            require(PinPolicy.valid(pin)) { "PIN must contain six ASCII digits" }
            vault.update { json ->
                check(!json.has("pinHash")) { "PIN already configured" }
                val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
                val hash = derive(pin, salt)
                try {
                    json.put("pinSalt", encoder.encodeToString(salt))
                        .put("pinHash", encoder.encodeToString(hash))
                        .put("pinIterations", PinPolicy.PBKDF2_ITERATIONS)
                    resetFailures(json)
                } finally { hash.fill(0); salt.fill(0) }
            }
        } finally { pin.fill('\u0000') }
    }

    override suspend fun verifyPin(pin: CharArray): PinResult {
        try {
            require(PinPolicy.valid(pin))
            return vault.update { json ->
                check(json.has("pinHash")) { "PIN has not been configured" }
                val remaining = remainingSeconds(json)
                if (remaining > 0) return@update PinResult.Locked(remaining)
                val salt = decoder.decode(json.getString("pinSalt"))
                val expected = decoder.decode(json.getString("pinHash"))
                val iterations = json.getInt("pinIterations")
                check(iterations == PinPolicy.PBKDF2_ITERATIONS) { "Unsupported PIN parameters" }
                val actual = derive(pin, salt)
                val matches = try { MessageDigest.isEqual(expected, actual) }
                    finally { actual.fill(0); expected.fill(0); salt.fill(0) }
                if (matches) {
                    resetFailures(json)
                    PinResult.Success
                } else {
                    val failures = (json.optInt("failures", 0) + 1).coerceAtMost(1000)
                    val delayMillis = PinPolicy.delaySeconds(failures) * 1000
                    json.put("failures", failures).put("delayMillis", delayMillis)
                        .put("failedAtWall", clock.millis()).put("failedAtElapsed", SystemClock.elapsedRealtime())
                        .put("failedAtBoot", bootCount())
                    if (delayMillis > 0) PinResult.Locked(delayMillis / 1000) else PinResult.Incorrect
                }
            }
        } finally { pin.fill('\u0000') }
    }

    override suspend fun lockoutSeconds(): Long = vault.read(::remainingSeconds)

    private fun remainingSeconds(json: JSONObject): Long {
        val delay = json.optLong("delayMillis", 0).coerceIn(0, 3_600_000)
        if (delay == 0L) return 0
        val elapsed = if (json.optInt("failedAtBoot", -2) == bootCount()) {
            SystemClock.elapsedRealtime() - json.optLong("failedAtElapsed", 0)
        } else {
            // Across reboot use wall time. A backward clock change never shortens a delay.
            clock.millis() - json.optLong("failedAtWall", clock.millis())
        }
        return ((delay - elapsed.coerceAtLeast(0)).coerceAtLeast(0) + 999) / 1000
    }
    private fun resetFailures(json: JSONObject) {
        json.put("failures", 0).put("delayMillis", 0)
            .remove("failedAtWall")
        json.remove("failedAtElapsed")
        json.remove("failedAtBoot")
    }
    private fun derive(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, PinPolicy.PBKDF2_ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
    }
}
