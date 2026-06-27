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

    // ── sendOtp ───────────────────────────────────────────────────
    suspend fun sendOtp(call: ApplicationCall) {
        val req    = call.receive<SendOtpRequest>()
        val result = sendOtpUseCase(req.phone)

        // ✅ Map بدل ApiResponse<T> Generic
        call.respond(HttpStatusCode.OK, mapOf(
            "success" to true,
            "data"    to mapOf(
                "phone"      to result.phone,
                "message"    to "OTP sent successfully",
                "otp"        to if (result.isMockMode) result.otpCode else null
            )
        ))
    }

    // ── verifyOtp ─────────────────────────────────────────────────
    suspend fun verifyOtp(call: ApplicationCall) {
        val req            = call.receive<VerifyOtpRequest>()
        val (tokens, user) = verifyOtpUseCase(req.phone, req.otp)

        call.respond(HttpStatusCode.OK, mapOf(
            "success" to true,
            "data"    to mapOf(
                "accessToken"  to tokens.accessToken,
                "refreshToken" to tokens.refreshToken,
                "expiresIn"    to tokens.expiresIn,
                "isNewUser"    to (user.name == null),
                "user"         to mapOf(
                    "id"              to user.id,
                    "phone"           to user.phone,
                    "name"            to user.name,
                    "bio"             to user.bio,
                    "profileImageUrl" to user.profileImageUrl,
                    "isVerified"      to user.isVerified
                )
            )
        ))
    }

    // ── refreshToken ──────────────────────────────────────────────
    suspend fun refreshToken(call: ApplicationCall) {
        val req    = call.receive<RefreshTokenRequest>()
        val tokens = refreshTokenUseCase(req.refreshToken)

        call.respond(HttpStatusCode.OK, mapOf(
            "success" to true,
            "data"    to mapOf(
                "accessToken"  to tokens.accessToken,
                "refreshToken" to tokens.refreshToken,
                "expiresIn"    to tokens.expiresIn
            )
        ))
    }

    // ── setupProfile ──────────────────────────────────────────────
    suspend fun setupProfile(call: ApplicationCall, userId: String) {
        val req  = call.receive<SetupProfileRequest>()
        val user = setupProfileUseCase(userId, req.name, req.bio, req.profileImageUrl)

        call.respond(HttpStatusCode.OK, mapOf(
            "success" to true,
            "data"    to mapOf(
                "id"              to user.id,
                "phone"           to user.phone,
                "name"            to user.name,
                "bio"             to user.bio,
                "profileImageUrl" to user.profileImageUrl,
                "isVerified"      to user.isVerified
            )
        ))
    }

    // ── logout ────────────────────────────────────────────────────
    suspend fun logout(call: ApplicationCall, userId: String) {
        logoutUseCase(userId)
        call.respond(HttpStatusCode.OK, mapOf(
            "success" to true,
            "data"    to mapOf("message" to "Logged out successfully")
        ))
    }

}
