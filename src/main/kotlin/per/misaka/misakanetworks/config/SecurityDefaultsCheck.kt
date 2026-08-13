package per.misaka.misakanetworks.config

import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration

/**
 * 启动安全检查：拒绝使用开发默认密钥启动，防止部署时忘配环境变量导致
 * JWT 可被伪造、图片加密形同虚设。
 */
@Configuration
class SecurityDefaultsCheck(private val appProperties: AppProperties) {

    private val devJwtSecret = "misakanetworks-dev-secret-change-me-0123456789abcdef"
    private val devImageKey = "misakanetworks-image-key-0123456"

    @PostConstruct
    fun validate() {
        require(appProperties.jwt.secret != devJwtSecret) {
            "JWT_SECRET 仍在使用开发默认值，出于安全考虑拒绝启动，请设置环境变量"
        }
        require(appProperties.images.encryptionKey != devImageKey) {
            "IMAGE_ENCRYPTION_KEY 仍在使用开发默认值，出于安全考虑拒绝启动，请设置环境变量"
        }
        require(appProperties.jwt.secret.toByteArray(Charsets.UTF_8).size >= 32) {
            "JWT_SECRET 至少需要 32 字节"
        }
        require(appProperties.images.encryptionKey.toByteArray(Charsets.UTF_8).size == 32) {
            "IMAGE_ENCRYPTION_KEY 必须恰好 32 字节"
        }
    }
}
