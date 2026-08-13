package per.misaka.misakanetworks.image

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import per.misaka.misakanetworks.config.AppProperties
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.nio.file.Files
import java.nio.file.Path

/** 本地目录存储（开发与测试用，默认） */
@Component
@ConditionalOnProperty(prefix = "app.images", name = ["storage-type"], havingValue = "local", matchIfMissing = true)
class LocalImageStorage(private val appProperties: AppProperties) : ImageStorage {

    override fun put(key: String, content: ByteArray): Mono<Void> =
        Mono.fromCallable<Void> {
            val dir = Path.of(appProperties.images.storageDir)
            Files.createDirectories(dir)
            Files.write(dir.resolve(key), content)
            null
        }.subscribeOn(Schedulers.boundedElastic())

    override fun get(key: String): Mono<ByteArray> =
        Mono.fromCallable {
            Files.readAllBytes(Path.of(appProperties.images.storageDir, key))
        }.subscribeOn(Schedulers.boundedElastic())
}
