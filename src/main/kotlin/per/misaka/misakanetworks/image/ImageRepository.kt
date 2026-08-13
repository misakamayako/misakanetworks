package per.misaka.misakanetworks.image

import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.data.repository.query.Param
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ImageRepository : ReactiveCrudRepository<Image, Long> {
    fun findByFileNameAndUserId(fileName: String, userId: Long): Mono<Image>

    fun findByIdAndUserId(id: Long, userId: Long): Mono<Image>

    @Query("SELECT * FROM images WHERE user_id = :userId ORDER BY created_at DESC, id DESC")
    fun findAllByUserId(@Param("userId") userId: Long): Flux<Image>

    @Query(
        "SELECT * FROM images WHERE user_id = :userId AND submission_status = :status " +
            "ORDER BY created_at DESC, id DESC",
    )
    fun findByUserIdAndSubmissionStatus(
        @Param("userId") userId: Long,
        @Param("status") status: String,
    ): Flux<Image>

    @Query(
        "SELECT * FROM images WHERE user_id = :userId " +
            "AND LOWER(sd_tags) LIKE LOWER(CONCAT('%', :tag, '%')) ORDER BY created_at DESC, id DESC",
    )
    fun findByUserIdAndTag(
        @Param("userId") userId: Long,
        @Param("tag") tag: String,
    ): Flux<Image>

    @Query(
        "SELECT * FROM images WHERE user_id = :userId AND submission_status = :status " +
            "AND LOWER(sd_tags) LIKE LOWER(CONCAT('%', :tag, '%')) ORDER BY created_at DESC, id DESC",
    )
    fun findByUserIdAndSubmissionStatusAndTag(
        @Param("userId") userId: Long,
        @Param("status") status: String,
        @Param("tag") tag: String,
    ): Flux<Image>

    @Query("SELECT COUNT(*) FROM images WHERE user_id = :userId")
    fun countByUserId(@Param("userId") userId: Long): Mono<Long>

    @Query("SELECT COUNT(*) FROM images WHERE user_id = :userId AND submission_status = :status")
    fun countByUserIdAndSubmissionStatus(
        @Param("userId") userId: Long,
        @Param("status") status: String,
    ): Mono<Long>

    @Query("SELECT COUNT(*) FROM images WHERE user_id = :userId AND LOWER(sd_tags) LIKE LOWER(CONCAT('%', :tag, '%'))")
    fun countByUserIdAndTag(
        @Param("userId") userId: Long,
        @Param("tag") tag: String,
    ): Mono<Long>

    @Query(
        "SELECT COUNT(*) FROM images WHERE user_id = :userId AND submission_status = :status " +
            "AND LOWER(sd_tags) LIKE LOWER(CONCAT('%', :tag, '%'))",
    )
    fun countByUserIdAndSubmissionStatusAndTag(
        @Param("userId") userId: Long,
        @Param("status") status: String,
        @Param("tag") tag: String,
    ): Mono<Long>
}
