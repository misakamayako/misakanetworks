package per.misaka.misakanetworks.article

import org.slf4j.LoggerFactory
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import per.misaka.misakanetworks.common.Errors
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class ArticleService(
    private val articleRepository: ArticleRepository,
    private val articleTagRepository: ArticleTagRepository,
    private val databaseClient: DatabaseClient,
) {

    private val log = LoggerFactory.getLogger(ArticleService::class.java)

    /** 全量上传：按 slug 幂等覆盖，新文章插入、老文章更新，tag 整体替换 */
    @Transactional
    fun upsertBatch(items: List<ArticleUpsertRequest>): Mono<BatchUpsertResponse> {
        return Flux.fromIterable(items)
            .flatMapSequential { upsertOne(it) }
            .count()
            .doOnNext { log.info("文章全量上传完成 processed={}", it) }
            .map { BatchUpsertResponse(it.toInt()) }
    }

    private fun upsertOne(item: ArticleUpsertRequest): Mono<Long> {
        validate(item)
        val now = Instant.now()
        return articleRepository.findBySlug(item.slug)
            .flatMap { existing ->
                articleRepository.save(existing.copy(title = item.title, summary = item.summary, updatedAt = now))
                    .flatMap { saved -> replaceTags(saved.id!!, item.tags).thenReturn(saved.id!!) }
            }
            .switchIfEmpty(
                Mono.defer {
                    articleRepository.save(
                        Article(slug = item.slug, title = item.title, summary = item.summary, createdAt = now, updatedAt = now),
                    ).flatMap { saved -> replaceTags(saved.id!!, item.tags).thenReturn(saved.id!!) }
                },
            )
    }

    private fun replaceTags(articleId: Long, tags: List<String>): Mono<Void> {
        val normalized = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return databaseClient.sql("DELETE FROM article_tags WHERE article_id = :articleId")
            .bind("articleId", articleId)
            .then()
            .thenMany(Flux.fromIterable(normalized).map { ArticleTag(articleId = articleId, tag = it) })
            .flatMap { articleTagRepository.save(it) }
            .then()
    }

    fun list(tag: String?): Mono<List<ArticleResponse>> {
        return articleRepository.findAllByOrderByCreatedAtDesc()
            .collectList()
            .flatMap { articles ->
                articleTagRepository.findAll()
                    .collectList()
                    .map { tagsByArticle ->
                        articles.map { article ->
                            toResponse(
                                article,
                                tagsByArticle.filter { it.articleId == article.id }.map { it.tag },
                            )
                        }
                    }
            }
            .map { list -> if (tag.isNullOrBlank()) list else list.filter { it.tags.contains(tag.trim()) } }
    }

    fun random(excludeSlug: String?): Mono<List<ArticleResponse>> {
        val articles = if (excludeSlug.isNullOrBlank()) {
            articleRepository.findRandom()
        } else {
            articleRepository.findRandomExcluding(excludeSlug)
        }
        return articles.collectList()
            .flatMap { list ->
                if (list.isEmpty()) {
                    Mono.just(emptyList())
                } else {
                    articleTagRepository.findAll().collectList().map { tagsByArticle ->
                        list.map { article ->
                            toResponse(
                                article,
                                tagsByArticle.filter { it.articleId == article.id }.map { it.tag },
                            )
                        }
                    }
                }
            }
    }

    fun getBySlug(slug: String): Mono<ArticleResponse> =
        articleRepository.findBySlug(slug)
            .switchIfEmpty(Mono.error(Errors.notFound("文章不存在")))
            .flatMap { article -> tagsOf(article.id!!).map { toResponse(article, it) } }

    fun tagStats(): Mono<List<TagCount>> =
        databaseClient.sql("SELECT tag, COUNT(*) AS cnt FROM article_tags GROUP BY tag ORDER BY cnt DESC, tag")
            .map { row, _ -> TagCount(row.get("tag", String::class.java)!!, row.get("cnt", Number::class.java)!!.toLong()) }
            .all()
            .collectList()

    private fun tagsOf(articleId: Long): Mono<List<String>> =
        articleTagRepository.findAllByArticleId(articleId).map { it.tag }.collectList()

    private fun toResponse(article: Article, tags: List<String>) = ArticleResponse(
        id = article.id!!,
        slug = article.slug,
        title = article.title,
        summary = article.summary,
        tags = tags.sorted(),
        createdAt = article.createdAt,
        updatedAt = article.updatedAt,
    )

    private fun validate(item: ArticleUpsertRequest) {
        if (item.slug.isBlank() || item.slug.length > 200) {
            throw Errors.badRequest("slug 不能为空且不超过 200 字符")
        }
        if (item.title.isBlank() || item.title.length > 255) {
            throw Errors.badRequest("标题不能为空且不超过 255 字符")
        }
        if ((item.summary?.length ?: 0) > 2000) {
            throw Errors.badRequest("摘要过长（最多 2000 字符）")
        }
        item.tags.forEach { tag ->
            if (tag.length > 64) throw Errors.badRequest("单个 tag 不能超过 64 字符")
        }
    }
}
