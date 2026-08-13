package per.misaka.misakanetworks.image

import org.springframework.stereotype.Service
import per.misaka.misakanetworks.config.AppProperties
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 图片静态加密：AES-256-GCM。
 * 文件格式：[12 字节随机 IV][GCM 密文+认证 tag]，自包含，可独立解密。
 */
@Service
class ImageCipher(appProperties: AppProperties) {

    private val key: SecretKeySpec
    private val ivLength = 12
    private val random = SecureRandom()

    init {
        val raw = appProperties.images.encryptionKey.toByteArray(Charsets.UTF_8)
        require(raw.size == 32) { "IMAGE_ENCRYPTION_KEY 必须是 32 字节（字符）" }
        key = SecretKeySpec(raw, "AES")
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(ivLength).also { random.nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray): ByteArray {
        require(data.size > ivLength) { "加密文件内容不完整" }
        val iv = data.copyOfRange(0, ivLength)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(data.copyOfRange(ivLength, data.size))
    }
}
