package per.misaka.misakanetworks

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import per.misaka.misakanetworks.security.TotpService
import java.security.MessageDigest
import java.time.Instant

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ImageIntegrationTest {

    @LocalServerPort
    var port: Int = 0

    lateinit var client: WebTestClient

    @Autowired
    lateinit var totpService: TotpService

    @BeforeEach
    fun setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    private fun multipart(
        imageBytes: ByteArray,
        sdTags: String? = null,
        status: String? = null,
        description: String? = null,
    ): MultiValueMap<String, HttpEntity<*>> {
        val builder = MultipartBodyBuilder()
        builder.part("file", ByteArrayResource(imageBytes), MediaType.IMAGE_PNG)
        if (sdTags != null) builder.part("sdTags", sdTags)
        if (status != null) builder.part("submissionStatus", status)
        if (description != null) builder.part("description", description)
        return builder.build()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun loginToken(email: String): String {
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
    fun imageUploadListUpdateDownload() {
        val token = loginToken("image@example.com")
        val img1 = ByteArray(64) { it.toByte() }
        val img2 = ByteArray(64) { (it + 1).toByte() }
        val img3 = ByteArray(64) { (it + 2).toByte() }
        val fileName1 = sha256Hex(img1)
        val fileName2 = sha256Hex(img2)
        val fileName3 = sha256Hex(img3)

        // 未登录上传被拒绝
        client.post().uri("/api/images")
            .body(BodyInserters.fromMultipartData(multipart(img1)))
            .exchange()
            .expectStatus().isUnauthorized

        // 上传成功，文件名 = 内容 SHA-256
        client.post().uri("/api/images")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .body(
                BodyInserters.fromMultipartData(
                    multipart(img1, "masterpiece, 1girl, forest", "NOT_SUBMITTED", "第一张测试图"),
                ),
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.fileName").isEqualTo(fileName1)
            .jsonPath("$.submissionStatus").isEqualTo("NOT_SUBMITTED")
            .jsonPath("$.sdTags").isEqualTo("masterpiece, 1girl, forest")
            .jsonPath("$.description").isEqualTo("第一张测试图")
            .jsonPath("$.url").isEqualTo("/api/images/file/$fileName1")

        // 相同内容重复上传：去重
        client.post().uri("/api/images")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .body(BodyInserters.fromMultipartData(multipart(img1)))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.fileName").isEqualTo(fileName1)

        // 再传两张不同的（第二张带 SD tag）
        client.post().uri("/api/images")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .body(BodyInserters.fromMultipartData(multipart(img2, "reactive, spring")))
            .exchange()
            .expectStatus().isOk
        client.post().uri("/api/images")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .body(BodyInserters.fromMultipartData(multipart(img3)))
            .exchange()
            .expectStatus().isOk

        // 分页列表：默认第一页、每页 20 条，按创建时间倒序（最新在前）
        client.get().uri("/api/images")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(3)
            .jsonPath("$.totalElements").isEqualTo(3)
            .jsonPath("$.totalPages").isEqualTo(1)
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.content[0].fileName").isEqualTo(fileName3)
            .jsonPath("$.content[2].fileName").isEqualTo(fileName1)

        // 按 tag 搜索（模糊、不区分大小写）
        client.get().uri("/api/images?tag=forest")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].fileName").isEqualTo(fileName1)

        client.get().uri("/api/images?tag=GIRL")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].fileName").isEqualTo(fileName1)

        client.get().uri("/api/images?tag=spring")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].fileName").isEqualTo(fileName2)

        // 搜不到的 tag 返回空
        client.get().uri("/api/images?tag=notexist")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.totalElements").isEqualTo(0)

        // 分页：每页 2 条，第二页剩 1 条
        client.get().uri("/api/images?page=0&size=2")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(2)
            .jsonPath("$.totalElements").isEqualTo(3)
            .jsonPath("$.totalPages").isEqualTo(2)
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.content[0].fileName").isEqualTo(fileName3)

        client.get().uri("/api/images?page=1&size=2")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].fileName").isEqualTo(fileName1)

        // 修改投稿状态
        client.patch().uri("/api/images/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .bodyValue(mapOf("submissionStatus" to "SUBMITTED"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.submissionStatus").isEqualTo("SUBMITTED")

        // 修改描述
        client.patch().uri("/api/images/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .bodyValue(mapOf("description" to "更新后的描述"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.description").isEqualTo("更新后的描述")

        // 按状态过滤 + 分页
        client.get().uri("/api/images?status=SUBMITTED")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].fileName").isEqualTo(fileName1)

        // status + tag 组合过滤
        client.get().uri("/api/images?status=SUBMITTED&tag=forest")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].fileName").isEqualTo(fileName1)

        // 未登录不能下载（私有）
        client.get().uri("/api/images/file/$fileName1")
            .exchange()
            .expectStatus().isUnauthorized

        // 下载解密后与原始内容一致
        client.get().uri("/api/images/file/$fileName1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.IMAGE_PNG)
            .expectBody(ByteArray::class.java)
            .isEqualTo(img1)

        // ---- 用户隔离：第二个用户上传的图，第一个用户看不到、也下不了 ----
        val token2 = loginToken("image2@example.com")
        val img4 = ByteArray(64) { (it + 3).toByte() }
        val fileName4 = sha256Hex(img4)
        client.post().uri("/api/images")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token2")
            .body(BodyInserters.fromMultipartData(multipart(img4)))
            .exchange()
            .expectStatus().isOk

        // 用户 1 的列表总数不变
        client.get().uri("/api/images")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.totalElements").isEqualTo(3)

        // 用户 1 下载用户 2 的文件 → 404
        client.get().uri("/api/images/file/$fileName4")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isNotFound

        // 用户 2 只能看到自己的 1 张
        client.get().uri("/api/images")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token2")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].fileName").isEqualTo(fileName4)
    }
}
