package per.misaka.misakanetworks.common

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * 完全拒绝跨域请求：只放行"无 Origin 头"（curl/服务端）和"Origin 与当前站点同源"的请求，
 * 其余一律 403。浏览器里前端必须与后端同源部署（或经反向代理）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class SameOriginFilter : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val request = exchange.request
        val origin = request.headers.getFirst(HttpHeaders.ORIGIN) ?: return chain.filter(exchange)
        val host = request.headers.getFirst(HttpHeaders.HOST)
        val scheme = request.uri.scheme
        val currentOrigin = if (host != null && scheme != null) "$scheme://$host" else null
        if (currentOrigin != null && origin == currentOrigin) {
            return chain.filter(exchange)
        }
        val response = exchange.response
        response.statusCode = HttpStatus.FORBIDDEN
        response.headers.contentType = MediaType.APPLICATION_JSON
        val body = """{"code":"forbidden","message":"跨域请求被拒绝"}""".toByteArray(Charsets.UTF_8)
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)))
    }
}
