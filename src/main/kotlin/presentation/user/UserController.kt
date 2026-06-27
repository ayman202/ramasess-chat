package presentation.user

import core.utils.BadRequestException
import core.utils.successResponse
import core.utils.userId
import domain.usecase.GetUserProfileUseCase
import domain.usecase.SearchUsersUseCase
import domain.usecase.UpdateUserProfileUseCase
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
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
        call.respond(HttpStatusCode.OK, successResponse {
            put("id",              user.id)
            put("phone",          user.phone)
            put("name",           user.name)
            put("bio",            user.bio)
            put("profileImageUrl",user.profileImageUrl)
            put("isOnline",       user.isOnline)
            put("lastSeen",       user.lastSeen)
            put("isVerified",     user.isVerified)
            put("createdAt",      user.createdAt)
        })
    }

    // PUT /users/me
    suspend fun updateMyProfile(call: ApplicationCall) {
        val req  = call.receive<UpdateProfileRequest>()
        val user = updateUserProfileUseCase(
            call.userId(), req.name, req.bio, req.profileImageUrl
        )
        call.respond(HttpStatusCode.OK, successResponse {
            put("id",              user.id)
            put("phone",          user.phone)
            put("name",           user.name)
            put("bio",            user.bio)
            put("profileImageUrl",user.profileImageUrl)
            put("isVerified",     user.isVerified)
        })
    }

    // GET /users/{id}
    suspend fun getUserById(call: ApplicationCall) {
        val id   = call.parameters["id"]
            ?: throw BadRequestException("Missing user id")
        val user = getUserProfileUseCase(id)
        call.respond(HttpStatusCode.OK, successResponse {
            put("id",              user.id)
            put("name",           user.name)
            put("bio",            user.bio)
            put("profileImageUrl",user.profileImageUrl)
            put("isOnline",       user.isOnline)
            put("lastSeen",       user.lastSeen)
        })
    }

    // GET /users/search?q=
    suspend fun searchUsers(call: ApplicationCall) {
        val query   = call.request.queryParameters["q"] ?: ""
        val results = searchUsersUseCase(query, call.userId())
        call.respond(HttpStatusCode.OK, successResponse {
            putJsonArray("users") {
                results.forEach { user ->
                    addJsonObject {
                        put("id",              user.id)
                        put("name",           user.name)
                        put("phone",          user.phone)
                        put("profileImageUrl",user.profileImageUrl)
                        put("isOnline",       user.isOnline)
                        put("lastSeen",       user.lastSeen)
                    }
                }
            }
        })
    }
}

fun Route.userRoutes() {
    val controller by inject<UserController>()
    route("/users") {
        get("/me")     { controller.getMyProfile(call) }
        put("/me")     { controller.updateMyProfile(call) }
        get("/search") { controller.searchUsers(call) }
        get("/{id}")   { controller.getUserById(call) }
    }
}
