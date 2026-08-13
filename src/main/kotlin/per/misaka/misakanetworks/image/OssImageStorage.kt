package per.misaka.misakanetworks.image

import com.aliyun.oss.OSS
import com.aliyun.oss.OSSClientBuilder
import jakarta.annotation.PreDestroy
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import per.misaka.misakanetworks.config.AppProperties
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.io.ByteArrayInputStream

/** 阿里云 OSS 私有桶存储：设置 IMAGE_STORAGE_TYPE=oss 后启用 */
@Component
@ConditionalOnProperty(prefix = "app.images", name = ["storage-type"], havingValue = "oss")
class OssImageStorage(appProperties: AppProperties) : ImageStorage {

    private val client: OSS = OSSClientBuilder().build(
        appProperties.images.endpoint,
        appProperties.images.accessKeyId,
        appProperties.images.accessKeySecret,
    )

    private val bucket: String = appProperties.images.bucket

    init {
        require(appProperties.images.endpoint.isNotBlank()) { "OSS_ENDPOINT 不能为空" }
        require(bucket.isNotBlank()) { "OSS_BUCKET 不能为空" }
    }

    override fun put(key: String, content: ByteArray): Mono<Void> =
        Mono.fromCallable<Void> {
            client.putObject(bucket, key, ByteArrayInputStream(content))
            null
        }.subscribeOn(Schedulers.boundedElastic())

    override fun get(key: String): Mono<ByteArray> =
        Mono.fromCallable {
            client.getObject(bucket, key).use { obj ->
                obj.objectContent.use { it.readBytes() }
            }
        }.subscribeOn(Schedulers.boundedElastic())

    @PreDestroy
    fun shutdown() {
        client.shutdown()
    }
}
