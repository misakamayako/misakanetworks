package per.misaka.misakanetworks.common

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(e: ApiException): ResponseEntity<Map<String, Any>> {
        log.warn("API 错误 [{}]: {}", e.code, e.message)
        return ResponseEntity.status(e.status).body(mapOf("code" to e.code, "message" to e.message))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<Map<String, Any>> {
        log.warn("参数错误: {}", e.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("code" to "bad_request", "message" to (e.message ?: "请求参数错误")))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<Map<String, Any>> {
        log.error("未处理的服务器异常", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("code" to "internal_error", "message" to "服务器内部错误"))
    }
}
