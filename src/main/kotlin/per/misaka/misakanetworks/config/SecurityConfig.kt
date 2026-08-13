package per.misaka.misakanetworks.config

import tools.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.server.ServerWebExchange
import per.misaka.misakanetworks.security.JwtAuthenticationFilter
import reactor.core.publisher.Mono

@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val appProperties: AppProperties,
    private val objectMapper: ObjectMapper,
) {

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .exceptionHandling { handling ->
                handling.authenticationEntryPoint { exchange, _ ->
                    jsonResponse(exchange, HttpStatus.UNAUTHORIZED, "unauthorized", "请先登录")
                }
                handling.accessDeniedHandler { exchange, _ ->
                    jsonResponse(exchange, HttpStatus.FORBIDDEN, "forbidden", "无权访问")
                }
            }
            .authorizeExchange { exchanges ->
                exchanges
                    .pathMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/mfa/bind")
                    .permitAll()
                    .pathMatchers(HttpMethod.POST, "/api/auth/password", "/api/articles/**", "/api/images/**")
                    .authenticated()
                    .pathMatchers(HttpMethod.PATCH, "/api/images/**")
                    .authenticated()
                    .pathMatchers(HttpMethod.GET, "/api/images/**")
                    .authenticated()
                    .pathMatchers(HttpMethod.GET, "/api/articles/**")
                    .permitAll()
                    .anyExchange()
                    .permitAll()
            }
            .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        return http.build()
    }

    private fun jsonResponse(exchange: ServerWebExchange, status: HttpStatus, code: String, message: String): Mono<Void> {
        val response = exchange.response
        response.statusCode = status
        response.headers.contentType = MediaType.APPLICATION_JSON
        val body = objectMapper.writeValueAsBytes(mapOf("code" to code, "message" to message))
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)))
    }
}
