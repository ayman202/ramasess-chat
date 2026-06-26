package presentation.user

import core.utils.BadRequestException
import core.utils.success
import core.utils.userId
import domain.usecase.GetUserProfileUseCase
import domain.usecase.SearchUsersUseCase
import domain.usecase.UpdateUserProfileUseCase
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import presentation.user.dto.UpdateProfileRequest

class UserController(
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val updateUserProfileUseCase: UpdateUserProfileUseCase,
    private val searchUsersUseCase: SearchUsersUseCase
) {

    // GET /users/me
    suspend fun getMyProfile(call: ApplicationCall) {
        val user = getUserProfileUseCase(call.userId())
        call.respond(HttpStatusCode.OK, success(user))
    }

    // PUT /users/me
    suspend fun updateMyProfile(call: ApplicationCall) {
        val req  = call.receive<UpdateProfileRequest>()
        val user = updateUserProfileUseCase(call.userId(), req.name, req.bio, req.profileImageUrl)
        call.respond(HttpStatusCode.OK, success(user))
    }

    // GET /users/{id}
    suspend fun getUserById(call: ApplicationCall) {
        val id   = call.parameters["id"] ?: throw BadRequestException("Missing user id")
        val user = getUserProfileUseCase(id)
        // إخفاء رقم الهاتف من الملف الشخصي للآخرين
        call.respond(HttpStatusCode.OK, success(user.copy(phone = "")))
    }

    // GET /users/search?q=
    suspend fun searchUsers(call: ApplicationCall) {
        val query   = call.request.queryParameters["q"] ?: ""
        val results = searchUsersUseCase(query, call.userId())
        call.respond(HttpStatusCode.OK, success(results))
    }
}

// ── Routes ────────────────────────────────────────────────────
fun Route.userRoutes() {
    val controller by inject<UserController>()

    route("/users") {
        get("/me")         { controller.getMyProfile(call) }
        put("/me")         { controller.updateMyProfile(call) }
        get("/search")     { controller.searchUsers(call) }
        get("/{id}")       { controller.getUserById(call) }
    }
}
