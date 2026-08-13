package per.misaka.misakanetworks.user

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import per.misaka.misakanetworks.common.ApiException
import per.misaka.misakanetworks.common.ApiRateLimiter
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val rateLimiter: ApiRateLimiter,
) {

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest, exchange: ServerWebExchange): Mono<RegisterResponse> =
        rateLimited("register", exchange, limit = 5, windowMs = 3_600_000) {
            authService.register(request)
        }

    @PostMapping("/mfa/bind")
    fun bindMfa(@RequestBody request: BindMfaRequest): Mono<MessageResponse> = authService.bindMfa(request)

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest, exchange: ServerWebExchange): Mono<LoginResponse> =
        rateLimited("login", exchange, limit = 10, windowMs = 15 * 60_000) {
            authService.login(request)
        }

    @PostMapping("/password")
    @SecurityRequirement(name = "bearerAuth")
    fun changePassword(@RequestBody request: ChangePasswordRequest): Mono<MessageResponse> =
        ReactiveSecurityContextHolder.getContext()
            .map { it.authentication!!.principal as Long }
            .flatMap { authService.changePassword(it, request) }

    private fun <T : Any> rateLimited(
        scope: String,
        exchange: ServerWebExchange,
        limit: Int,
        windowMs: Long,
        block: () -> Mono<T>,
    ): Mono<T> {
        val ip = exchange.request.remoteAddress?.address?.hostAddress ?: "unknown"
        return if (rateLimiter.allow("$scope:$ip", limit, windowMs)) {
            block()
        } else {
            Mono.error(ApiException(HttpStatus.TOO_MANY_REQUESTS, "too_many_requests", "请求过于频繁，请稍后再试"))
        }
    }
}
