package per.misaka.misakanetworks.common

import org.springframework.stereotype.Component
import per.misaka.misakanetworks.config.AppProperties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/** 简单的内存滑动窗口限流（按 key，如 IP），可整体开关 */
@Component
class ApiRateLimiter(private val appProperties: AppProperties) {

    private val windows = ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>>()

    fun allow(key: String, limit: Int, windowMs: Long): Boolean {
        if (!appProperties.security.rateLimitEnabled) return true
        val now = System.currentTimeMillis()
        val deque = windows.computeIfAbsent(key) { ConcurrentLinkedDeque() }
        synchronized(deque) {
            while (true) {
                val oldest = deque.peekFirst() ?: break
                if (now - oldest >= windowMs) deque.pollFirst() else break
            }
            if (deque.size >= limit) return false
            deque.addLast(now)
            return true
        }
    }
}
