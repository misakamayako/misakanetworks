package per.misaka.misakanetworks.security

import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
class JwtAuthenticationFilter(private val jwtService: JwtService) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val header = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION) ?: return chain.filter(exchange)
        if (!header.startsWith("Bearer ")) return chain.filter(exchange)
        val token = header.removePrefix("Bearer ").trim()
        if (token.isEmpty()) return chain.filter(exchange)
        return jwtService.parse(token)
            .flatMap { claims ->
                val authentication = UsernamePasswordAuthenticationToken(
                    jwtService.userIdOf(claims),
                    token,
                    listOf(SimpleGrantedAuthority("ROLE_USER")),
                )
                chain.filter(exchange).contextWrite(
                    ReactiveSecurityContextHolder.withSecurityContext(Mono.just(SecurityContextImpl(authentication))),
                )
            }
            .onErrorResume { chain.filter(exchange) }
    }
}
