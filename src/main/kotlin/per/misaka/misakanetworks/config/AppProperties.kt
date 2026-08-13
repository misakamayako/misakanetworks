package per.misaka.misakanetworks.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val jwt: Jwt = Jwt(),
    val totp: Totp = Totp(),
    val images: Images = Images(),
    val security: Security = Security(),
) {
    data class Jwt(
        var secret: String = "misakanetworks-dev-secret-change-me-0123456789abcdef",
        var accessTtlMinutes: Long = 1440,
        var bindTtlMinutes: Long = 10,
    )

    data class Totp(var issuer: String = "MisakaNetworks")

    data class Images(
        var storageDir: String = "./data/images",
        var encryptionKey: String = "misakanetworks-image-key-0123456",
        var storageType: String = "local",
        var endpoint: String = "",
        var accessKeyId: String = "",
        var accessKeySecret: String = "",
        var bucket: String = "",
    )

    data class Security(var rateLimitEnabled: Boolean = true)
}
