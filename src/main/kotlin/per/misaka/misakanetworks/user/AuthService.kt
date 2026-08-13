package per.misaka.misakanetworks.user

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import per.misaka.misakanetworks.common.ApiException
import per.misaka.misakanetworks.common.Errors
import per.misaka.misakanetworks.config.AppProperties
import per.misaka.misakanetworks.security.JwtService
import per.misaka.misakanetworks.security.TotpService
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val totpService: TotpService,
    private val jwtService: JwtService,
    private val appProperties: AppProperties,
) {

    private val log = LoggerFactory.getLogger(AuthService::class.java)
    private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    @Transactional
    fun register(request: RegisterRequest): Mono<RegisterResponse> {
        val email = request.email.trim().lowercase()
        val password = request.password
        if (!emailPattern.matches(email)) return Mono.error(Errors.badRequest("邮箱格式不正确"))
        if (password.length < 8) return Mono.error(Errors.badRequest("密码至少 8 位"))

        return userRepository.findByEmail(email)
            .flatMap<RegisterResponse> { Mono.error(Errors.conflict("该邮箱已注册")) }
            .switchIfEmpty(
                Mono.defer {
                    val secret = totpService.generateSecret()
                    val user = User(
                        email = email,
                        passwordHash = passwordEncoder.encode(password)!!,
                        totpSecret = secret,
                    )
                    userRepository.save(user).map { saved ->
                        val userId = saved.id!!
                        log.info("注册成功 userId={} email={}", userId, email)
                        RegisterResponse(
                            userId = userId,
                            bindToken = jwtService.issueBindToken(userId),
                            otpauthUri = totpService.otpauthUri(secret, email),
                        )
                    }
                },
            )
    }

    fun bindMfa(request: BindMfaRequest): Mono<MessageResponse> {
        return jwtService.parse(request.bindToken)
            .onErrorResume { Mono.error(Errors.badRequest("绑定凭证无效或已过期")) }
            .flatMap { claims ->
                if (claims["type"] != "mfa-bind") {
                    Mono.error(Errors.badRequest("绑定凭证类型错误"))
                } else {
                    userRepository.findById(jwtService.userIdOf(claims))
                        .switchIfEmpty(Mono.error(Errors.notFound("用户不存在")))
                        .flatMap { user ->
                            when {
                                user.totpBound -> Mono.error(Errors.badRequest("已完成绑定，无需重复操作"))
                                !totpService.verify(user.totpSecret, request.code) ->
                                    Mono.error(Errors.badRequest("动态密码不正确（服务器当前时间：${Instant.now()}）"))
                                else -> {
                            val updated = user.copy(totpBound = true, updatedAt = Instant.now())
                            userRepository.save(updated).map {
                                log.info("MFA 绑定成功 userId={}", user.id)
                                MessageResponse("绑定成功")
                            }
                                }
                            }
                        }
                }
            }
    }

    fun login(request: LoginRequest): Mono<LoginResponse> {
        val email = request.email.trim().lowercase()
        return userRepository.findByEmail(email)
            .switchIfEmpty(Mono.error(Errors.unauthorized("邮箱或密码错误")))
            .flatMap { user ->
                when {
                    !passwordEncoder.matches(request.password, user.passwordHash) -> {
                        log.info("登录失败 email={} 原因=密码错误", email)
                        Mono.error(Errors.unauthorized("邮箱或密码错误"))
                    }
                    !user.totpBound -> {
                        log.info("登录失败 email={} 原因=未绑定MFA", email)
                        Mono.error(ApiException(HttpStatus.FORBIDDEN, "mfa_not_bound", "请先完成动态密码绑定"))
                    }
                    request.code.isNullOrBlank() -> {
                        log.info("登录失败 email={} 原因=缺少动态码", email)
                        Mono.error(ApiException(HttpStatus.UNAUTHORIZED, "mfa_required", "请输入动态密码"))
                    }
                    !totpService.verify(user.totpSecret, request.code) -> {
                        log.info("登录失败 email={} 原因=动态码不正确", email)
                        Mono.error(Errors.unauthorized("动态密码不正确（服务器当前时间：${Instant.now()}）"))
                    }
                    else -> {
                        val userId = user.id!!
                        log.info("登录成功 userId={} email={}", userId, user.email)
                        Mono.just(
                            LoginResponse(
                                accessToken = jwtService.issueAccessToken(userId),
                                expiresIn = appProperties.jwt.accessTtlMinutes * 60,
                                email = user.email,
                            ),
                        )
                    }
                }
            }
    }

    @Transactional
    fun changePassword(userId: Long, request: ChangePasswordRequest): Mono<MessageResponse> {
        if (request.newPassword.length < 8) return Mono.error(Errors.badRequest("新密码至少 8 位"))
        return userRepository.findById(userId)
            .switchIfEmpty(Mono.error(Errors.notFound("用户不存在")))
            .flatMap { user ->
                if (!passwordEncoder.matches(request.oldPassword, user.passwordHash)) {
                    Mono.error(Errors.badRequest("原密码不正确"))
                } else {
                    val updated = user.copy(
                        passwordHash = passwordEncoder.encode(request.newPassword)!!,
                        updatedAt = Instant.now(),
                    )
                    userRepository.save(updated).map {
                        log.info("密码已修改 userId={}", userId)
                        MessageResponse("密码已修改")
                    }
                }
            }
    }
}
