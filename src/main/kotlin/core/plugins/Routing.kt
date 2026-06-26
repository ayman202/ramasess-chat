package core.plugins

import presentation.auth.authRoutes
import presentation.chat.chatRoutes
import presentation.chat.configureWebSocketRoutes
import presentation.contacts.contactsRoutes
import presentation.media.mediaRoutes
import presentation.story.storyRoutes
import presentation.user.userRoutes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {

    // ── WebSocket (auth مدمج في الـ Handler) ──────────────
    configureWebSocketRoutes()

    routing {

        // ── Health Check  ──────────────────────────────────
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf(
                "status"  to "UP",
                "service" to "chat-backend",
                "version" to "1.0.0"
            ))
        }

        // ── API v1 ─────────────────────────────────────────
        route("/api/v1") {

            // بدون JWT
            authRoutes()

            // محمية بـ JWT
            authenticate("auth-jwt") {
                userRoutes()
                chatRoutes()
                storyRoutes()
                contactsRoutes()
                mediaRoutes()
            }
        }
    }
}
