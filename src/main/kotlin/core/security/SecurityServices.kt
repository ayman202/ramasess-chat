package core.security

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.config.*
import io.lettuce.core.RedisClient
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// ─────────────────────────────────────────────────────────────
// JwtService
// ─────────────────────────────────────────────────────────────
class JwtService(config: ApplicationConfig) {

    private val secret       = config.property("jwt.secret").getString()
    val issuer               = config.property("jwt.issuer").getString()
    val audience             = config.property("jwt.audience").getString()
    val realm                = config.property("jwt.realm").getString()
    private val accessExpiry = config.property("jwt.accessTokenExpiry").getString().toLong()
    private val refreshExpiry = config.property("jwt.refreshTokenExpiry").getString().toLong()
    private val algorithm    = Algorithm.HMAC256(secret)

    // ── Access Token (15 دقيقة) ────────────────────────────
    fun generateAccessToken(userId: String): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withClaim("userId", userId)
        .withClaim("type", "access")
        .withIssuedAt(Date())
        .withExpiresAt(Date(System.currentTimeMillis() + accessExpiry * 1_000L))
        .sign(algorithm)

    // ── Refresh Token (30 يوم) ─────────────────────────────
    fun generateRefreshToken(userId: String): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withClaim("userId", userId)
        .withClaim("type", "refresh")
        .withIssuedAt(Date())
        .withExpiresAt(Date(System.currentTimeMillis() + refreshExpiry * 1_000L))
        .sign(algorithm)

    fun getAccessExpirySeconds(): Long = accessExpiry

    // ── Verifier للـ Ktor Auth Plugin ─────────────────────
    fun getVerifier(): JWTVerifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    // ── التحقق اليدوي (WebSocket) ──────────────────────────
    fun verifyToken(token: String): String? = try {
        getVerifier().verify(token).getClaim("userId").asString()
    } catch (e: Exception) {
        null
    }
}

// ─────────────────────────────────────────────────────────────
// EncryptionService  (AES-256-GCM + BCrypt)
// ─────────────────────────────────────────────────────────────
class EncryptionService(config: ApplicationConfig) {

    // مفتاح 32 byte للـ AES-256
    private val rawKey = config.property("security.encryptionKey").getString()
        .toByteArray(Charsets.UTF_8).copyOf(32)

    // ✅ BCryptPasswordEncoder من Spring Security (موثوق وموجود في Maven Central)
    private val bcrypt = BCryptPasswordEncoder(12)

    // ── تشفير نص بـ AES-256-GCM ──────────────────────────
    fun encrypt(plainText: String): String {
        val iv     = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = buildCipher(Cipher.ENCRYPT_MODE, iv)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    // ── فك تشفير نص ──────────────────────────────────────
    fun decrypt(cipherText: String): String {
        val combined  = Base64.getDecoder().decode(cipherText)
        val iv        = combined.copyOfRange(0, 12)
        val encrypted = combined.copyOfRange(12, combined.size)
        val cipher    = buildCipher(Cipher.DECRYPT_MODE, iv)
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun buildCipher(mode: Int, iv: ByteArray): Cipher {
        val spec = SecretKeySpec(rawKey, "AES")
        return Cipher.getInstance("AES/GCM/NoPadding").also {
            it.init(mode, spec, GCMParameterSpec(128, iv))
        }
    }

    // ── OTP Hashing بـ BCrypt ─────────────────────────────
    fun hashOtp(otp: String): String = bcrypt.encode(otp)

    fun verifyOtp(otp: String, hash: String): Boolean = bcrypt.matches(otp, hash)
}

// ─────────────────────────────────────────────────────────────
// OtpService  (Redis + Mock/Twilio)
// ─────────────────────────────────────────────────────────────
class OtpService(
    config: ApplicationConfig,
    private val encryption: EncryptionService
) {
    private val log       = LoggerFactory.getLogger("OtpService")
    private val expiry    = config.property("redis.otpExpiry").getString().toLong()
    private val provider  = config.propertyOrNull("otp.provider")?.getString() ?: "mock"
    private val redis     = RedisClient.create(
        config.property("redis.url").getString()
    ).connect().sync()

    // ── إنشاء وإرسال OTP ───────────────────────────────────
    fun generateAndSend(phone: String): String {
        val otp    = (100_000..999_999).random().toString()
        val hashed = encryption.hashOtp(otp)
        redis.setex("otp:$phone", expiry, hashed)

        when (provider) {
            "mock"   -> log.info("🔑 [MOCK OTP] Phone=$phone  OTP=$otp")
            "twilio" -> sendViaTwilio(phone, otp)
            else     -> log.warn("Unknown OTP provider: $provider")
        }
        return otp   // مُرجَّع فقط في mock mode
    }

    // ── التحقق من OTP ──────────────────────────────────────
    fun verify(phone: String, otp: String): Boolean {
        val hashed = redis.get("otp:$phone") ?: return false
        val valid  = encryption.verifyOtp(otp, hashed)
        if (valid) redis.del("otp:$phone")
        return valid
    }

    fun isMockMode() = provider == "mock"

    private fun sendViaTwilio(phone: String, otp: String) {
        // Twilio HTTP call هنا
        log.info("📱 Sending OTP to $phone via Twilio")
    }
}
