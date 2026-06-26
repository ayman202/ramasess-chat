package presentation.auth

import core.utils.success
import domain.usecase.LogoutUseCase
import domain.usecase.RefreshTokenUseCase
import domain.usecase.SendOtpUseCase
import domain.usecase.SetupProfileUseCase
import domain.usecase.VerifyOtpUseCase
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import presentation.auth.dto.AuthResponse
import presentation.auth.dto.RefreshTokenRequest
import presentation.auth.dto.SendOtpRequest
import presentation.auth.dto.SetupProfileRequest
import presentation.auth.dto.UserDto
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
        val req = call.receive<SendOtpRequest>()
        val result = sendOtpUseCase(req.phone)

        val data = buildMap<String, Any?> {
            put("phone", result.phone)
            put("message", "OTP sent successfully")
            if (result.isMockMode) put("otp", result.otpCode)  // فقط في التطوير
        }
        call.respond(HttpStatusCode.OK, success(data))
    }

    // POST /auth/verify-otp
    suspend fun verifyOtp(call: ApplicationCall) {
        val req = call.receive<VerifyOtpRequest>()
        val (tokens, user) = verifyOtpUseCase(req.phone, req.otp)

        val response = AuthResponse(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresIn = tokens.expiresIn,
            isNewUser = user.name == null,
            user = UserDto(
                id = user.id,
                phone = user.phone,
                name = user.name,
                bio = user.bio,
                profileImageUrl = user.profileImageUrl,
                isVerified = user.isVerified
            )
        )
        call.respond(HttpStatusCode.OK, success(response))
    }

    // POST /auth/refresh-token
    suspend fun refreshToken(call: ApplicationCall) {
        val req    = call.receive<RefreshTokenRequest>()
        val tokens = refreshTokenUseCase(req.refreshToken)
        call.respond(HttpStatusCode.OK, success(mapOf(
            "accessToken"  to tokens.accessToken,
            "refreshToken" to tokens.refreshToken,
            "expiresIn"    to tokens.expiresIn
        )))
    }

    // POST /auth/setup-profile  (محمية بـ JWT)
    suspend fun setupProfile(call: ApplicationCall, userId: String) {
        val req  = call.receive<SetupProfileRequest>()
        val user = setupProfileUseCase(userId, req.name, req.bio, req.profileImageUrl)
        call.respond(HttpStatusCode.OK, success(
            UserDto(
                id = user.id,
                phone = user.phone,
                name = user.name,
                bio = user.bio,
                profileImageUrl = user.profileImageUrl,
                isVerified = user.isVerified
            ), "Profile setup complete"))
    }

    // POST /auth/logout
    suspend fun logout(call: ApplicationCall, userId: String) {
        logoutUseCase(userId)
        call.respond(HttpStatusCode.OK, success(mapOf("message" to "Logged out successfully")))
    }
}
