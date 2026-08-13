package per.misaka.misakanetworks.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import per.misaka.misakanetworks.config.AppProperties
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(private val appProperties: AppProperties) {

    private val key: SecretKey = Keys.hmacShaKeyFor(appProperties.jwt.secret.toByteArray(Charsets.UTF_8))

    fun issueBindToken(userId: Long): String =
        issue(userId, mapOf("type" to "mfa-bind"), Duration.ofMinutes(appProperties.jwt.bindTtlMinutes))

    fun issueAccessToken(userId: Long): String =
        issue(userId, mapOf("type" to "access"), Duration.ofMinutes(appProperties.jwt.accessTtlMinutes))

    fun parse(token: String): Mono<Claims> = Mono.fromCallable {
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
    }.subscribeOn(Schedulers.boundedElastic())

    fun userIdOf(claims: Claims): Long = claims.subject.toLong()

    private fun issue(userId: Long, claims: Map<String, Any>, ttl: Duration): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(userId.toString())
            .claims(claims)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(key)
            .compact()
    }
}
