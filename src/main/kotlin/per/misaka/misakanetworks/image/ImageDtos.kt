package per.misaka.misakanetworks.image

import java.time.Instant

data class ImageResponse(
    val id: Long,
    val userId: Long,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val submissionStatus: String,
    val sdTags: String?,
    val description: String?,
    val createdAt: Instant,
    /** 相对地址，前端以自己的站点地址拼接即可 */
    val url: String,
)

data class ImageUpdateRequest(
    val submissionStatus: String? = null,
    val sdTags: String? = null,
    val description: String? = null,
)

data class ImagePageResponse(
    val content: List<ImageResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Long,
)

data class DecryptedImage(val bytes: ByteArray, val contentType: String)
