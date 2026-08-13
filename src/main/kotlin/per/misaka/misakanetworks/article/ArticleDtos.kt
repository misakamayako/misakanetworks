package per.misaka.misakanetworks.article

import java.time.Instant

data class ArticleUpsertRequest(
    val slug: String,
    val title: String,
    val summary: String? = null,
    val tags: List<String> = emptyList(),
)

data class ArticleResponse(
    val id: Long,
    val slug: String,
    val title: String,
    val summary: String?,
    val tags: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class BatchUpsertResponse(val processed: Int)

data class TagCount(val tag: String, val count: Long)
