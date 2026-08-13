package per.misaka.misakanetworks.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun misakaOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("misakanetworks 博客后端 API")
                .description("纯服务端博客数据与认证接口，供 Astro 前端调用。需要登录的接口使用 JWT：Authorization: Bearer <token>。")
                .version("1.0.0"),
        )
        .components(
            Components().addSecuritySchemes(
                "bearerAuth",
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT"),
            ),
        )
}
