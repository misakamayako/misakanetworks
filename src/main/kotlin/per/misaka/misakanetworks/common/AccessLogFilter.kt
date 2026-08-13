package per.misaka.misakanetworks.common

import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/** 全接口访问日志：方法、路径、状态码、耗时 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class AccessLogFilter : WebFilter {

    private val log = LoggerFactory.getLogger(AccessLogFilter::class.java)

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val request = exchange.request
        val start = System.nanoTime()
        return chain.filter(exchange).doFinally {
            val status = exchange.response.statusCode?.value() ?: 0
            val durationMs = (System.nanoTime() - start) / 1_000_000
            val query = request.uri.rawQuery?.let { "?$it" } ?: ""
            log.info("{} {}{} -> {} ({} ms)", request.method, request.path, query, status, durationMs)
        }
    }
}
