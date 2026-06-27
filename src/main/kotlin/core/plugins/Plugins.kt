package core.plugins

import core.security.JwtService
import core.utils.BadRequestException
import core.utils.ConflictException
import core.utils.ForbiddenException
import core.utils.NotFoundException
import core.utils.UnauthorizedException
import core.utils.failure
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.path
import io.ktor.server.response.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.slf4j.event.Level
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint       = false
            isLenient         = true
            ignoreUnknownKeys = true
            encodeDefaults    = true
        })
    }
}

fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/api") }
    }
}

fun Application.configureCompression() {
    install(Compression) {
        gzip    { priority = 1.0 }
        deflate { priority = 0.9 }
    }
}

fun Application.configureCors() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Refresh-Token")
        anyHost()
        allowCredentials = true
    }
}

fun Application.configureSecurity() {
    val jwtService by inject<JwtService>()

    install(Authentication) {
        jwt("auth-jwt") {
            realm = jwtService.realm
            verifier(jwtService.getVerifier())   // ✅ صح
            validate { credential ->
                val userId = credential.payload.getClaim("userId")?.asString()
                val type   = credential.payload.getClaim("type")?.asString()
                if (!userId.isNullOrBlank() && type == "access") {
                    JWTPrincipal(credential.payload)
                } else null
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    failure("UNAUTHORIZED", "Token is invalid or expired")
                )
            }
        }
    }
}

fun Application.configureWebSockets() {
    install(WebSockets) {
        pingPeriod   = 30.seconds
        timeout      = 60.seconds
        maxFrameSize = 64 * 1024L
        masking      = false
    }
}

fun Application.configureRateLimiting() {
    install(RateLimit) {
        global {
            rateLimiter(limit = 100, refillPeriod = 1.minutes)
        }
        register(RateLimitName("otp")) {
            rateLimiter(limit = 5, refillPeriod = 1.minutes)
        }
        register(RateLimitName("upload")) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
        }
    }
}

fun Application.configureStatusPages() {
    install(StatusPages) {

        exception<NotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                mapOf("success" to false, "error" to mapOf(
                    "code" to "NOT_FOUND", "details" to cause.message
                ))
            )
        }
        exception<UnauthorizedException> { call, cause ->
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("success" to false, "error" to mapOf(
                    "code" to "UNAUTHORIZED", "details" to cause.message
                ))
            )
        }
        exception<ForbiddenException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("success" to false, "error" to mapOf(
                    "code" to "FORBIDDEN", "details" to cause.message
                ))
            )
        }
        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("success" to false, "error" to mapOf(
                    "code" to "BAD_REQUEST", "details" to cause.message
                ))
            )
        }
        exception<ConflictException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                mapOf("success" to false, "error" to mapOf(
                    "code" to "CONFLICT", "details" to cause.message
                ))
            )
        }
        exception<Exception> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("success" to false, "error" to mapOf(
                    "code" to "INTERNAL_ERROR",
                    "details" to (cause.message ?: "An unexpected error occurred")
                ))
            )
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(
                HttpStatusCode.NotFound,
                mapOf("success" to false, "error" to mapOf(
                    "code" to "NOT_FOUND", "details" to "Route not found"
                ))
            )
        }
        status(HttpStatusCode.MethodNotAllowed) { call, _ ->
            call.respond(
                HttpStatusCode.MethodNotAllowed,
                mapOf("success" to false, "error" to mapOf(
                    "code" to "METHOD_NOT_ALLOWED", "details" to "Method not allowed"
                ))
            )
        }
    }
}