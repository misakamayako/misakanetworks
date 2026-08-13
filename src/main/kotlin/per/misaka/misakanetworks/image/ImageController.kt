package per.misaka.misakanetworks.image

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.Part
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.publisher.Flux

@RestController
@RequestMapping("/api/images")
@SecurityRequirement(name = "bearerAuth")
class ImageController(private val imageService: ImageService) {

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(@RequestBody parts: Flux<Part>): Mono<ImageResponse> =
        currentUserId().flatMap { imageService.upload(parts, it) }

    @GetMapping
    fun list(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) tag: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): Mono<ImagePageResponse> =
        currentUserId().flatMap {
            imageService.list(status, tag, page.coerceAtLeast(0), size.coerceIn(1, 100), it)
        }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): Mono<ImageResponse> =
        currentUserId().flatMap { imageService.get(id, it) }

    @PatchMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: ImageUpdateRequest): Mono<ImageResponse> =
        currentUserId().flatMap { imageService.update(id, request, it) }

    @GetMapping("/file/{fileName}")
    fun file(@PathVariable fileName: String): Mono<ResponseEntity<ByteArray>> =
        currentUserId().flatMap { imageService.file(fileName, it) }.map { decrypted ->
            ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(decrypted.contentType))
                .body(decrypted.bytes)
        }

    /** 当前登录用户的 id（JWT subject） */
    private fun currentUserId(): Mono<Long> =
        ReactiveSecurityContextHolder.getContext().map { it.authentication!!.principal as Long }
}
