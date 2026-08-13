package per.misaka.misakanetworks.image

import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.codec.multipart.FormFieldPart
import org.springframework.http.codec.multipart.Part
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import per.misaka.misakanetworks.common.Errors
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat

@Service
class ImageService(
    private val imageRepository: ImageRepository,
    private val imageCipher: ImageCipher,
    private val imageStorage: ImageStorage,
) {

    private val log = LoggerFactory.getLogger(ImageService::class.java)

    companion object {
        val SUBMISSION_STATUSES = setOf("SUBMITTED", "NOT_SUBMITTED", "NOT_AS_SUBMISSION")
        const val MAX_IMAGE_BYTES = 20L * 1024 * 1024
    }

    /** 解析 multipart：file 为文件部分，sdTags / submissionStatus 为普通表单字段 */
    @Transactional
    fun upload(parts: Flux<Part>, userId: Long): Mono<ImageResponse> =
        parts.collectList().flatMap { list ->
            val filePart = list.firstOrNull { it.name() == "file" }
                ?: return@flatMap Mono.error(Errors.badRequest("缺少 file 文件"))
            val fields = list.filterIsInstance<FormFieldPart>().associate { it.name() to it.value() }
            upload(filePart, fields["sdTags"], fields["submissionStatus"], fields["description"], userId)
        }

    private fun upload(
        file: Part,
        sdTags: String?,
        submissionStatus: String?,
        description: String?,
        userId: Long,
    ): Mono<ImageResponse> {
        val contentLength = file.headers().contentLength
        if (contentLength > MAX_IMAGE_BYTES) {
            return Mono.error(Errors.badRequest("图片过大（最大 ${MAX_IMAGE_BYTES / 1024 / 1024}MB）"))
        }
        val contentType = file.headers().contentType?.toString() ?: "application/octet-stream"
        return DataBufferUtils.join(file.content())
            .map { toByteArray(it) }
            .flatMap { bytes -> doUpload(bytes, contentType, sdTags, submissionStatus, description, userId) }
    }

    private fun doUpload(
        bytes: ByteArray,
        contentType: String,
        sdTags: String?,
        submissionStatus: String?,
        description: String?,
        userId: Long,
    ): Mono<ImageResponse> {
        if (bytes.isEmpty()) return Mono.error(Errors.badRequest("文件为空"))
        if (bytes.size.toLong() > MAX_IMAGE_BYTES) {
            return Mono.error(Errors.badRequest("图片过大（最大 ${MAX_IMAGE_BYTES / 1024 / 1024}MB）"))
        }
        val fileName = sha256Hex(bytes)
        val status = normalizeStatus(submissionStatus)
        return imageRepository.findByFileNameAndUserId(fileName, userId)
            // 内容相同（特征值相同）则返回已有记录，避免重复存储
            .flatMap { existing ->
                log.info("图片内容重复，返回已有记录 fileName={}", fileName)
                Mono.just(toResponse(existing))
            }
            .switchIfEmpty(
                Mono.defer {
                    imageStorage.put("$fileName.enc", imageCipher.encrypt(bytes))
                        .then(
                            imageRepository.save(
                                Image(
                                    userId = userId,
                                    fileName = fileName,
                                    contentType = contentType,
                                    sizeBytes = bytes.size.toLong(),
                                    submissionStatus = status,
                                    sdTags = sdTags?.trim()?.takeIf { it.isNotEmpty() },
                                    description = description?.trim()?.takeIf { it.isNotEmpty() },
                                ),
                            ),
                        )
                        .map {
                            log.info("图片上传成功 fileName={} size={}", fileName, bytes.size)
                            toResponse(it)
                        }
                },
            )
    }

    /** 分页列表：按创建时间倒序（新的在第一页），可选按投稿状态、SD tag 过滤（tag 模糊匹配，不区分大小写） */
    fun list(submissionStatus: String?, tag: String?, page: Int, size: Int, userId: Long): Mono<ImagePageResponse> {
        val status = submissionStatus?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        val tagQuery = tag?.trim()?.takeIf { it.isNotEmpty() }

        val contentMono: Mono<List<Image>>
        val totalMono: Mono<Long>
        when {
            status != null && tagQuery != null -> {
                contentMono = imageRepository.findByUserIdAndSubmissionStatusAndTag(userId, status, tagQuery).collectList()
                totalMono = imageRepository.countByUserIdAndSubmissionStatusAndTag(userId, status, tagQuery)
            }
            status != null -> {
                contentMono = imageRepository.findByUserIdAndSubmissionStatus(userId, status).collectList()
                totalMono = imageRepository.countByUserIdAndSubmissionStatus(userId, status)
            }
            tagQuery != null -> {
                contentMono = imageRepository.findByUserIdAndTag(userId, tagQuery).collectList()
                totalMono = imageRepository.countByUserIdAndTag(userId, tagQuery)
            }
            else -> {
                contentMono = imageRepository.findAllByUserId(userId).collectList()
                totalMono = imageRepository.countByUserId(userId)
            }
        }
        return Mono.zip(contentMono, totalMono).map { zip ->
            ImagePageResponse(
                content = zip.t1.drop(page * size).take(size).map(::toResponse),
                page = page,
                size = size,
                totalElements = zip.t2,
                totalPages = if (zip.t2 == 0L) 0L else (zip.t2 + size - 1) / size,
            )
        }
    }

    fun get(id: Long, userId: Long): Mono<ImageResponse> =
        imageRepository.findByIdAndUserId(id, userId)
            .switchIfEmpty(Mono.error(Errors.notFound("图片不存在")))
            .map(::toResponse)

    @Transactional
    fun update(id: Long, request: ImageUpdateRequest, userId: Long): Mono<ImageResponse> =
        imageRepository.findByIdAndUserId(id, userId)
            .switchIfEmpty(Mono.error(Errors.notFound("图片不存在")))
            .flatMap { record ->
                if (request.submissionStatus == null && request.sdTags == null && request.description == null) {
                    Mono.error(Errors.badRequest("没有需要更新的字段"))
                } else {
                    val newStatus = request.submissionStatus?.let { normalizeStatus(it) } ?: record.submissionStatus
                    val newTags = request.sdTags?.trim()?.takeIf { it.isNotEmpty() } ?: record.sdTags
                    val newDescription = request.description?.trim()?.takeIf { it.isNotEmpty() } ?: record.description
                    imageRepository.save(
                        record.copy(
                            submissionStatus = newStatus,
                            sdTags = newTags,
                            description = newDescription,
                            updatedAt = Instant.now(),
                        ),
                    ).map {
                        log.info("图片信息已更新 id={} 投稿状态={}", id, newStatus)
                        toResponse(it)
                    }
                }
            }

    /** 读取加密文件并解密，供前端展示 */
    fun file(fileName: String, userId: Long): Mono<DecryptedImage> =
        imageRepository.findByFileNameAndUserId(fileName, userId)
            .switchIfEmpty(Mono.error(Errors.notFound("图片不存在")))
            .flatMap { record ->
                imageStorage.get("$fileName.enc")
                    .map {
                        log.debug("图片下载解密 fileName={}", fileName)
                        DecryptedImage(imageCipher.decrypt(it), record.contentType)
                    }
            }

    private fun normalizeStatus(status: String?): String {
        val normalized = status?.trim()?.uppercase() ?: "NOT_SUBMITTED"
        require(normalized in SUBMISSION_STATUSES) {
            "投稿状态只能是 SUBMITTED / NOT_SUBMITTED / NOT_AS_SUBMISSION"
        }
        return normalized
    }

    private fun sha256Hex(bytes: ByteArray): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun toByteArray(buffer: DataBuffer): ByteArray {
        val bytes = ByteArray(buffer.readableByteCount())
        buffer.read(bytes)
        DataBufferUtils.release(buffer)
        return bytes
    }

    private fun toResponse(image: Image) = ImageResponse(
        id = image.id!!,
        userId = image.userId,
        fileName = image.fileName,
        contentType = image.contentType,
        sizeBytes = image.sizeBytes,
        submissionStatus = image.submissionStatus,
        sdTags = image.sdTags,
        description = image.description,
        createdAt = image.createdAt,
        url = "/api/images/file/${image.fileName}",
    )
}
