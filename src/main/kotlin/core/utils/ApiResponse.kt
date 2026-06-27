package core.utils

import io.ktor.server.application.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.principal
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────
// Response Wrappers
// ─────────────────────────────────────────────────────────────
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val message: String?        = null,
    val data: T?                = null,
    val error: ApiError?        = null,
    val pagination: Pagination? = null
)

@Serializable
data class ApiError(
    val code: String,
    val details: String? = null
)

@Serializable
data class Pagination(
    val page: Int,
    val pageSize: Int,
    val total: Long,
    val hasNext: Boolean
)
// ─────────────────────────────────────────────────────────────
// Helper Builders
// ─────────────────────────────────────────────────────────────
fun <T> success(data: T, message: String? = null) =
    ApiResponse(success = true, message = message, data = data)

fun <T> successPaged(data: T, pagination: Pagination, message: String? = null) =
    ApiResponse(success = true, message = message, data = data, pagination = pagination)

fun failure(code: String, details: String? = null) =
    ApiResponse<Nothing>(success = false, error = ApiError(code, details))

// ─────────────────────────────────────────────────────────────
// ApplicationCall Extensions
// ─────────────────────────────────────────────────────────────

// ── الحصول على userId من الـ JWT ────────────────────────────
fun ApplicationCall.userId(): String =
    principal<JWTPrincipal>()
        ?.payload
        ?.getClaim("userId")
        ?.asString()
        ?: throw UnauthorizedException("Missing userId in token")

// ── Pagination helpers ────────────────────────────────────────
fun ApplicationCall.page()     = request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
fun ApplicationCall.pageSize() = request.queryParameters["pageSize"]?.toIntOrNull()?.coerceIn(1, 100) ?: 30

// ─────────────────────────────────────────────────────────────
// Custom Exceptions
// ─────────────────────────────────────────────────────────────
class NotFoundException(message: String)     : Exception(message)
class UnauthorizedException(message: String) : Exception(message)
class BadRequestException(message: String)   : Exception(message)
class ForbiddenException(message: String)    : Exception(message)
class ConflictException(message: String)     : Exception(message)
