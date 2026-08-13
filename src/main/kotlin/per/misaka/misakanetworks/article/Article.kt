package per.misaka.misakanetworks.article

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("articles")
data class Article(
    @Id val id: Long? = null,
    val slug: String,
    val title: String,
    val summary: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

@Table("article_tags")
data class ArticleTag(
    @Id val id: Long? = null,
    val articleId: Long,
    val tag: String,
)
