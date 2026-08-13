package per.misaka.misakanetworks

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.WebTestClient
import per.misaka.misakanetworks.security.TotpService
import java.time.Instant

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowIntegrationTest {

    @LocalServerPort
    var port: Int = 0

    lateinit var client: WebTestClient

    @Autowired
    lateinit var totpService: TotpService

    @BeforeEach
    fun setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    @Test
    fun registerBindLoginChangePassword() {
        val email = "admin@example.com"
        val password = "Password123!"

        val registerBody = client.post().uri("/api/auth/register")
            .bodyValue(mapOf("email" to email, "password" to password))
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult().responseBody!!
        val bindToken = registerBody["bindToken"] as String
        val otpauthUri = registerBody["otpauthUri"] as String
        val secret = Regex("secret=([^&]+)").find(otpauthUri)!!.groupValues[1]

        // 重复注册应冲突
        client.post().uri("/api/auth/register")
            .bodyValue(mapOf("email" to email, "password" to password))
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)

        // 错误的动态码应被拒绝
        client.post().uri("/api/auth/mfa/bind")
            .bodyValue(mapOf("bindToken" to bindToken, "code" to "000000"))
            .exchange()
            .expectStatus().isBadRequest

        // 正确的动态码绑定成功
        client.post().uri("/api/auth/mfa/bind")
            .bodyValue(mapOf("bindToken" to bindToken, "code" to totpService.code(secret, Instant.now())))
            .exchange()
            .expectStatus().isOk

        // 绑定后不输动态码无法登录
        client.post().uri("/api/auth/login")
            .bodyValue(mapOf("email" to email, "password" to password))
            .exchange()
            .expectStatus().isUnauthorized

        // 输入动态码登录成功
        val token = client.post().uri("/api/auth/login")
            .bodyValue(
                mapOf(
                    "email" to email,
                    "password" to password,
                    "code" to totpService.code(secret, Instant.now()),
                ),
            )
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult().responseBody!!["accessToken"] as String

        // 修改密码
        client.post().uri("/api/auth/password")
            .header("Authorization", "Bearer $token")
            .bodyValue(mapOf("oldPassword" to password, "newPassword" to "NewPassword456!"))
            .exchange()
            .expectStatus().isOk

        // 用新密码 + 动态码重新登录
        client.post().uri("/api/auth/login")
            .bodyValue(
                mapOf(
                    "email" to email,
                    "password" to "NewPassword456!",
                    "code" to totpService.code(secret, Instant.now()),
                ),
            )
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun crossOriginRequestsAreRejected() {
        // 跨域请求被完全拒绝
        client.get().uri("/api/articles")
            .header(HttpHeaders.ORIGIN, "http://evil.example.com")
            .exchange()
            .expectStatus().isForbidden

        // 同源请求正常放行
        client.get().uri("/api/articles")
            .header(HttpHeaders.ORIGIN, "http://localhost:$port")
            .exchange()
            .expectStatus().isOk
    }
}
