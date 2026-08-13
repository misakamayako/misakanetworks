package per.misaka.misakanetworks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import per.misaka.misakanetworks.config.AppProperties
import per.misaka.misakanetworks.security.TotpService
import java.time.Instant

class TotpServiceTest {

    private val service = TotpService(AppProperties())

    // RFC 6238 附录 B 的 SHA1 测试向量（密钥 = ASCII "12345678901234567890"）
    private val rfcSecret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    @Test
    fun matchesRfc6238Sha1Vectors() {
        val vectors = mapOf(
            59L to "287082",
            1111111109L to "081804",
            1111111111L to "050471",
            1234567890L to "005924",
            2000000000L to "279037",
            20000000000L to "353130",
        )
        vectors.forEach { (epochSeconds, expected) ->
            assertEquals(expected, service.code(rfcSecret, Instant.ofEpochSecond(epochSeconds)), "epoch=$epochSeconds")
        }
    }

    @Test
    fun verifyAcceptsCurrentAndAdjacentStepsButRejectsWrongCode() {
        val secret = service.generateSecret()
        val now = Instant.now()
        assertTrue(service.verify(secret, service.code(secret, now)))
        assertTrue(service.verify(secret, service.code(secret, now.plusSeconds(30))))
        assertTrue(service.verify(secret, service.code(secret, now.minusSeconds(30))))
        assertFalse(service.verify(secret, "000000"))
    }
}
