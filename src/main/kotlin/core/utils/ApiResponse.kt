package core.utils

import io.ktor.server.application.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.principal
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

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
fun successResponse(block: JsonObjectBuilder.() -> Unit): JsonObject {
    return buildJsonObject {
        put("success", true)
        putJsonObject("data", block)
    }
}

fun errorResponse(code: String, details: String?): JsonObject {
    return buildJsonObject {
        put("success", false)
        putJsonObject("error") {
            put("code", code)
            put("details", details)
        }
    }
}

fun pagedResponse(
    total: Long,
    page: Int,
    pageSize: Int,
    block: JsonObjectBuilder.() -> Unit
): JsonObject {
    return buildJsonObject {
        put("success", true)
        putJsonObject("data", block)
        putJsonObject("pagination") {
            put("page", page)
            put("pageSize", pageSize)
            put("total", total)
            put("hasNext", total > page * pageSize)
        }
    }
}

// ── Extensions ────────────────────────────────────────────────

fun ApplicationCall.userId(): String =
    principal<JWTPrincipal>()
        ?.payload
        ?.getClaim("userId")
        ?.asString()
        ?: throw UnauthorizedException("Missing userId in token")

fun ApplicationCall.page() =
    request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1

fun ApplicationCall.pageSize() =
    request.queryParameters["pageSize"]?.toIntOrNull()?.coerceIn(1, 100) ?: 30

// ── Custom Exceptions ─────────────────────────────────────────

class NotFoundException(message: String)     : Exception(message)
class UnauthorizedException(message: String) : Exception(message)
class BadRequestException(message: String)   : Exception(message)
class ForbiddenException(message: String)    : Exception(message)
class ConflictException(message: String)     : Exception(message)
