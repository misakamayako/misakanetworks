package per.misaka.misakanetworks.image

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("images")
data class Image(
    @Id val id: Long? = null,
    val userId: Long,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val submissionStatus: String,
    val sdTags: String? = null,
    val description: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
