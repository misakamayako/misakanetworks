package per.misaka.misakanetworks.common

import org.springframework.http.HttpStatus

class ApiException(val status: HttpStatus, val code: String, override val message: String) : RuntimeException(message)

object Errors {
    fun badRequest(message: String) = ApiException(HttpStatus.BAD_REQUEST, "bad_request", message)
    fun unauthorized(message: String = "未认证或凭证无效") = ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", message)
    fun forbidden(message: String = "无权访问") = ApiException(HttpStatus.FORBIDDEN, "forbidden", message)
    fun notFound(message: String) = ApiException(HttpStatus.NOT_FOUND, "not_found", message)
    fun conflict(message: String) = ApiException(HttpStatus.CONFLICT, "conflict", message)
}
