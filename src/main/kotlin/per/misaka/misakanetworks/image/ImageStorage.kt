package per.misaka.misakanetworks.image

import reactor.core.publisher.Mono

/** 图片加密文件的存储后端抽象：本地目录 / 阿里云 OSS 私有桶 */
interface ImageStorage {
    fun put(key: String, content: ByteArray): Mono<Void>

    fun get(key: String): Mono<ByteArray>
}
