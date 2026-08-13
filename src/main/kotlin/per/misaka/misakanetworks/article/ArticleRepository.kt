package per.misaka.misakanetworks.article

import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.data.repository.query.Param
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ArticleRepository : ReactiveCrudRepository<Article, Long> {
    fun findBySlug(slug: String): Mono<Article>

    fun findAllByOrderByCreatedAtDesc(): Flux<Article>

    @Query("SELECT * FROM articles ORDER BY RAND() LIMIT 3")
    fun findRandom(): Flux<Article>

    @Query("SELECT * FROM articles WHERE slug <> :excludeSlug ORDER BY RAND() LIMIT 3")
    fun findRandomExcluding(@Param("excludeSlug") excludeSlug: String): Flux<Article>
}

interface ArticleTagRepository : ReactiveCrudRepository<ArticleTag, Long> {
    fun findAllByArticleId(articleId: Long): Flux<ArticleTag>
}
