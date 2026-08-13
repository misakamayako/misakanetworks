package per.misaka.misakanetworks.article

import org.springframework.http.HttpHeaders
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/articles")
class ArticleController(private val articleService: ArticleService) {

    private val blogRefererPattern = Regex("""(?:^|/)blog/([^/?#]+)""")

    @GetMapping
    fun list(@RequestParam(required = false) tag: String?): Mono<List<ArticleResponse>> = articleService.list(tag)

    @GetMapping("/random")
    fun random(
        @RequestHeader(value = HttpHeaders.REFERER, required = false) referer: String?,
    ): Mono<List<ArticleResponse>> = articleService.random(extractSlugFromReferer(referer))

    @GetMapping("/stats/tags")
    fun tagStats(): Mono<List<TagCount>> = articleService.tagStats()

    @GetMapping("/{slug}")
    fun get(@PathVariable slug: String): Mono<ArticleResponse> = articleService.getBySlug(slug)

    @PostMapping("/batch")
    @SecurityRequirement(name = "bearerAuth")
    fun upsertBatch(@RequestBody items: List<ArticleUpsertRequest>): Mono<BatchUpsertResponse> =
        articleService.upsertBatch(items)

    /** 从 Referer 中解析 /blog/{slug}/ 格式的文章 slug，不匹配则返回 null */
    private fun extractSlugFromReferer(referer: String?): String? =
        referer?.let { blogRefererPattern.find(it)?.groupValues?.get(1) }
}
