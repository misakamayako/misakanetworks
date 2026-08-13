package per.misaka.misakanetworks.user

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("users")
data class User(
    @Id val id: Long? = null,
    val email: String,
    val passwordHash: String,
    val totpSecret: String,
    val totpBound: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
