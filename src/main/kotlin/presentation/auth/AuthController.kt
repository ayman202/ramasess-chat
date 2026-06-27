package presentation.auth

import domain.usecase.LogoutUseCase
import domain.usecase.RefreshTokenUseCase
import domain.usecase.SendOtpUseCase
import domain.usecase.SetupProfileUseCase
import domain.usecase.VerifyOtpUseCase
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import presentation.auth.dto.RefreshTokenRequest
import presentation.auth.dto.SendOtpRequest
import presentation.auth.dto.SetupProfileRequest
import presentation.auth.dto.VerifyOtpRequest

class AuthController(
    private val sendOtpUseCase: SendOtpUseCase,
    private val verifyOtpUseCase: VerifyOtpUseCase,
    private val refreshTokenUseCase: RefreshTokenUseCase,
    private val setupProfileUseCase: SetupProfileUseCase,
    private val logoutUseCase: LogoutUseCase
) {

    // POST /auth/send-otp
    suspend fun sendOtp(call: ApplicationCall) {
        val req    = call.receive<SendOtpRequest>()
        val result = sendOtpUseCase(req.phone)

        call.respond(HttpStatusCode.OK, buildJsonObject {
            put("success", true)
            putJsonObject("data") {
                put("phone",   result.phone)
                put("message", "OTP sent successfully")
                if (result.isMockMode) put("otp", result.otpCode)
            }
        })
    }

    // POST /auth/verify-otp
    suspend fun verifyOtp(call: ApplicationCall) {
        val req            = call.receive<VerifyOtpRequest>()
        val (tokens, user) = verifyOtpUseCase(req.phone, req.otp)

        call.respond(HttpStatusCode.OK, buildJsonObject {
            put("success", true)
            putJsonObject("data") {
                put("accessToken",  tokens.accessToken)
                put("refreshToken", tokens.refreshToken)
                put("expiresIn",    tokens.expiresIn)
                put("isNewUser",    user.name == null)
                putJsonObject("user") {
                    put("id",              user.id)
                    put("phone",           user.phone)
                    put("name",            user.name)
                    put("bio",             user.bio)
                    put("profileImageUrl", user.profileImageUrl)
                    put("isVerified",      user.isVerified)
                }
            }
        })
    }

    // POST /auth/refresh-token
    suspend fun refreshToken(call: ApplicationCall) {
        val req    = call.receive<RefreshTokenRequest>()
        val tokens = refreshTokenUseCase(req.refreshToken)

        call.respond(HttpStatusCode.OK, buildJsonObject {
            put("success", true)
            putJsonObject("data") {
                put("accessToken",  tokens.accessToken)
                put("refreshToken", tokens.refreshToken)
                put("expiresIn",    tokens.expiresIn)
            }
        })
    }

    // POST /auth/setup-profile
    suspend fun setupProfile(call: ApplicationCall, userId: String) {
        val req  = call.receive<SetupProfileRequest>()
        val user = setupProfileUseCase(userId, req.name, req.bio, req.profileImageUrl)

        call.respond(HttpStatusCode.OK, buildJsonObject {
            put("success", true)
            putJsonObject("data") {
                put("id",              user.id)
                put("phone",           user.phone)
                put("name",            user.name)
                put("bio",             user.bio)
                put("profileImageUrl", user.profileImageUrl)
                put("isVerified",      user.isVerified)
            }
        })
    }

    // POST /auth/logout
    suspend fun logout(call: ApplicationCall, userId: String) {
        logoutUseCase(userId)

        call.respond(HttpStatusCode.OK, buildJsonObject {
            put("success", true)
            putJsonObject("data") {
                put("message", "Logged out successfully")
            }
        })
    }
}