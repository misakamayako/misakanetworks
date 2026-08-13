package per.misaka.misakanetworks

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import per.misaka.misakanetworks.common.ApiRateLimiter
import per.misaka.misakanetworks.config.AppProperties

class ApiRateLimiterTest {

    @Test
    fun enforcesLimitAndExpiresAfterWindow() {
        val limiter = ApiRateLimiter(AppProperties())
        val key = "login:127.0.0.1"

        repeat(10) { assertTrue(limiter.allow(key, 10, 1000), "第 ${it + 1} 次应放行") }
        assertFalse(limiter.allow(key, 10, 1000), "超过上限应拒绝")

        Thread.sleep(1100)
        assertTrue(limiter.allow(key, 10, 1000), "窗口过期后应重新放行")
    }
}
