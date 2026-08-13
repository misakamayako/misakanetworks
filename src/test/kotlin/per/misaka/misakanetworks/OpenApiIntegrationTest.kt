package per.misaka.misakanetworks

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiIntegrationTest {

    @LocalServerPort
    var port: Int = 0

    lateinit var client: WebTestClient

    @BeforeEach
    fun setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    @Test
    fun openApiDocsAreExposed() {
        client.get().uri("/v3/api-docs")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.openapi").exists()
            .jsonPath("$.paths./api/auth/register").exists()
            .jsonPath("$.paths./api/auth/mfa/bind").exists()
            .jsonPath("$.paths./api/auth/login").exists()
            .jsonPath("$.paths./api/auth/password").exists()
            .jsonPath("$.paths./api/articles/batch").exists()
            .jsonPath("$.paths./api/articles/random").exists()
            .jsonPath("$.paths./api/articles/stats/tags").exists()
            .jsonPath("$.components.securitySchemes.bearerAuth").exists()
    }

    @Test
    fun swaggerUiIsServed() {
        client.get().uri("/swagger-ui/index.html")
            .exchange()
            .expectStatus().isOk
    }
}
