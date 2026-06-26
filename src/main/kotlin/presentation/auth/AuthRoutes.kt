package presentation.auth

import core.utils.userId
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.authRoutes() {
    val controller by inject<AuthController>()

    route("/auth") {
        rateLimit(RateLimitName("otp")) {
            post("/send-otp") { controller.sendOtp(call) }
        }
        post("/verify-otp")    { controller.verifyOtp(call) }
        post("/refresh-token") { controller.refreshToken(call) }

        authenticate("auth-jwt") {
            post("/setup-profile") { controller.setupProfile(call, call.userId()) }
            post("/logout")        { controller.logout(call, call.userId()) }
        }
    }
}