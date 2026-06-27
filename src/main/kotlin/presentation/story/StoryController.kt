package presentation.story

import domain.model.StoryMediaType
import presentation.story.dto.CreateStoryRequest
import core.utils.BadRequestException
import core.utils.successResponse
import core.utils.userId
import domain.usecase.CreateStoryUseCase
import domain.usecase.DeleteStoryUseCase
import domain.usecase.GetStoriesUseCase
import domain.usecase.ViewStoryUseCase
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.koin.ktor.ext.inject

class StoryController(
    private val createStoryUseCase: CreateStoryUseCase,
    private val getStoriesUseCase: GetStoriesUseCase,
    private val deleteStoryUseCase: DeleteStoryUseCase,
    private val viewStoryUseCase: ViewStoryUseCase
) {

    // GET /stories
    suspend fun getStories(call: ApplicationCall) {
        val stories = getStoriesUseCase(call.userId())
        call.respond(HttpStatusCode.OK, successResponse {
            putJsonArray("stories") {
                stories.forEach { story ->
                    addJsonObject {
                        put("id",          story.id)
                        put("userId",      story.userId)
                        put("userName",    story.userName)
                        put("userAvatar",  story.userAvatar)
                        put("mediaUrl",    story.mediaUrl)
                        put("mediaType",   story.mediaType.name)
                        put("caption",     story.caption)
                        put("viewsCount",  story.viewsCount)
                        put("isViewed",    story.isViewed)
                        put("createdAt",   story.createdAt)
                        put("expiresAt",   story.expiresAt)
                    }
                }
            }
        })
    }

    // POST /stories
    suspend fun createStory(call: ApplicationCall) {
        val req  = call.receive<CreateStoryRequest>()
        val type = runCatching { StoryMediaType.valueOf(req.mediaType) }
            .getOrElse { throw BadRequestException("Invalid media type") }

        val story = createStoryUseCase(call.userId(), req.mediaUrl, type, req.caption)
        call.respond(HttpStatusCode.Created, successResponse {
            put("id",        story.id)
            put("mediaUrl",  story.mediaUrl)
            put("mediaType", story.mediaType.name)
            put("caption",   story.caption)
            put("createdAt", story.createdAt)
            put("expiresAt", story.expiresAt)
        })
    }

    // DELETE /stories/{id}
    suspend fun deleteStory(call: ApplicationCall) {
        val storyId = call.parameters["id"]
            ?: throw BadRequestException("Missing story id")
        deleteStoryUseCase(storyId, call.userId())
        call.respond(HttpStatusCode.OK, successResponse {
            put("deleted", true)
        })
    }

    // POST /stories/{id}/view
    suspend fun viewStory(call: ApplicationCall) {
        val storyId = call.parameters["id"]
            ?: throw BadRequestException("Missing story id")
        viewStoryUseCase(storyId, call.userId())
        call.respond(HttpStatusCode.OK, successResponse {
            put("viewed", true)
        })
    }
}

fun Route.storyRoutes() {
    val controller by inject<StoryController>()
    route("/stories") {
        get                { controller.getStories(call) }
        post               { controller.createStory(call) }
        delete("/{id}")    { controller.deleteStory(call) }
        post("/{id}/view") { controller.viewStory(call) }
    }
}
