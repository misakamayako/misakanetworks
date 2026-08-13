package per.misaka.misakanetworks.security

import org.apache.commons.codec.binary.Base32
import org.springframework.stereotype.Service
import per.misaka.misakanetworks.config.AppProperties
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class TotpService(private val appProperties: AppProperties) {

    private val base32 = Base32()
    private val random = SecureRandom()
    private val stepSeconds = 30L
    private val digits = 6

    fun generateSecret(): String {
        val bytes = ByteArray(20)
        random.nextBytes(bytes)
        return base32.encodeToString(bytes).trimEnd('=').uppercase()
    }

    fun otpauthUri(secret: String, account: String): String {
        val issuer = appProperties.totp.issuer
        val encodedIssuer = URLEncoder.encode(issuer, Charsets.UTF_8)
        val encodedAccount = URLEncoder.encode(account, Charsets.UTF_8)
        return "otpauth://totp/$encodedIssuer:$encodedAccount?secret=$secret" +
            "&issuer=$encodedIssuer&algorithm=SHA1&digits=$digits&period=$stepSeconds"
    }

    fun verify(secret: String, code: String): Boolean {
        if (code.length != digits || code.any { !it.isDigit() }) return false
        val now = Instant.now()
        return (-1L..1L).any { offset ->
            MessageDigest.isEqual(
                code(secret, now.plusSeconds(offset * stepSeconds)).toByteArray(Charsets.US_ASCII),
                code.toByteArray(Charsets.US_ASCII),
            )
        }
    }

    /** 计算指定时刻的 TOTP 动态码（测试与校验共用） */
    fun code(secret: String, at: Instant): String {
        val counter = at.epochSecond / stepSeconds
        val key = base32.decode(pad(secret))
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
        val offset = hash[hash.size - 1].toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        return (binary % 1_000_000).toString().padStart(digits, '0')
    }

    private fun pad(secret: String): String {
        val raw = secret.uppercase().replace(" ", "")
        return raw + "=".repeat(((8 - raw.length % 8) % 8))
    }
}
