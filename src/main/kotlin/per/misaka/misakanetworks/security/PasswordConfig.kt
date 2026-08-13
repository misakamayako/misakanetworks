package per.misaka.misakanetworks.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder

@Configuration
class PasswordConfig {

    // PBKDF2WithHmacSHA256（185000 次迭代、16 字节盐、256 位密钥）
    @Bean
    fun passwordEncoder(): PasswordEncoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()
}
