package per.misaka.misakanetworks

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import per.misaka.misakanetworks.security.TotpService
import java.time.Instant

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArticleIntegrationTest {

    @LocalServerPort
    var port: Int = 0

    lateinit var client: WebTestClient

    @Autowired
    lateinit var totpService: TotpService

    @BeforeEach
    fun setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    private fun loginToken(): String {
        val email = "writer@example.com"
        val password = "Password123!"
        val registerBody = client.post().uri("/api/auth/register")
            .bodyValue(mapOf("email" to email, "password" to password))
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult().responseBody!!
        val secret = Regex("secret=([^&]+)").find(registerBody["otpauthUri"] as String)!!.groupValues[1]
        client.post().uri("/api/auth/mfa/bind")
            .bodyValue(mapOf("bindToken" to registerBody["bindToken"], "code" to totpService.code(secret, Instant.now())))
            .exchange()
            .expectStatus().isOk
        val loginBody = client.post().uri("/api/auth/login")
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
            .returnResult().responseBody!!
        return loginBody["accessToken"] as String
    }

    @Test
    fun articleLifecycle() {
        val token = loginToken()
        val batch = listOf(
            mapOf(
                "slug" to "hello-world",
                "title" to "Hello World",
                "summary" to "第一篇",
                "tags" to listOf("java", "spring"),
            ),
            mapOf(
                "slug" to "reactive-notes",
                "title" to "Reactive Notes",
                "summary" to "第二篇",
                "tags" to listOf("java"),
            ),
            mapOf(
                "slug" to "life",
                "title" to "Life",
                "summary" to "第三篇",
                "tags" to listOf("随笔"),
            ),
            mapOf(
                "slug" to "fourth",
                "title" to "Fourth",
                "summary" to "第四篇",
                "tags" to emptyList<String>(),
            ),
        )

        // 未登录上传被拒绝
        client.post().uri("/api/articles/batch")
            .bodyValue(batch)
            .exchange()
            .expectStatus().isUnauthorized

        // 全量上传（含新老文章）
        client.post().uri("/api/articles/batch")
            .header("Authorization", "Bearer $token")
            .bodyValue(batch)
            .exchange()
            .expectStatus().isOk

        // 重复全量上传应幂等
        client.post().uri("/api/articles/batch")
            .header("Authorization", "Bearer $token")
            .bodyValue(batch)
            .exchange()
            .expectStatus().isOk

        // 列表：4 篇，tags 正确
        client.get().uri("/api/articles")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(4)
            .jsonPath("$[?(@.slug=='hello-world')].tags[0]").isEqualTo("java")

        // 按 tag 过滤
        client.get().uri("/api/articles?tag=java")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)

        // 每 tag 文章数统计
        client.get().uri("/api/articles/stats/tags")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(3)
            .jsonPath("$[?(@.tag=='java')].count").isEqualTo(2)

        // 随机 3 篇（不带 Referer）
        client.get().uri("/api/articles/random")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(3)

        // 带 Referer 时随机 3 篇且排除当前文章
        client.get().uri("/api/articles/random")
            .header(HttpHeaders.REFERER, "http://localhost:4321/blog/hello-world/")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(3)
            .jsonPath("$[?(@.slug=='hello-world')]").isEmpty()

        // 按 slug 查询
        client.get().uri("/api/articles/hello-world")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.title").isEqualTo("Hello World")

        // 不存在的 slug 返回 404
        client.get().uri("/api/articles/not-exist")
            .exchange()
            .expectStatus().isNotFound
    }
}
