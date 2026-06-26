package presentation.story

import domain.model.StoryMediaType
import presentation.story.dto.CreateStoryRequest
import core.utils.BadRequestException
import core.utils.success
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
import org.koin.ktor.ext.inject

class StoryController(
    private val createStoryUseCase: CreateStoryUseCase,
    private val getStoriesUseCase: GetStoriesUseCase,
    private val deleteStoryUseCase: DeleteStoryUseCase,
    private val viewStoryUseCase: ViewStoryUseCase
) {

    // GET /stories  → كل الاستوريات من جهات الاتصال
    suspend fun getStories(call: ApplicationCall) {
        val stories = getStoriesUseCase(call.userId())
        call.respond(HttpStatusCode.OK, success(stories))
    }

    // POST /stories
    suspend fun createStory(call: ApplicationCall) {
        val req  = call.receive<CreateStoryRequest>()
        val type = runCatching { StoryMediaType.valueOf(req.mediaType) }
            .getOrElse { throw BadRequestException("Invalid media type. Use IMAGE or VIDEO") }

        val story = createStoryUseCase(call.userId(), req.mediaUrl, type, req.caption)
        call.respond(HttpStatusCode.Created, success(story))
    }

    // DELETE /stories/{id}
    suspend fun deleteStory(call: ApplicationCall) {
        val storyId = call.parameters["id"] ?: throw BadRequestException("Missing story id")
        deleteStoryUseCase(storyId, call.userId())
        call.respond(HttpStatusCode.OK, success(mapOf("deleted" to true)))
    }

    // POST /stories/{id}/view
    suspend fun viewStory(call: ApplicationCall) {
        val storyId = call.parameters["id"] ?: throw BadRequestException("Missing story id")
        viewStoryUseCase(storyId, call.userId())
        call.respond(HttpStatusCode.OK, success(mapOf("viewed" to true)))
    }
}

// ── Routes ────────────────────────────────────────────────────
fun Route.storyRoutes() {
    val controller by inject<StoryController>()

    route("/stories") {
        get                      { controller.getStories(call) }
        post                     { controller.createStory(call) }
        delete("/{id}")          { controller.deleteStory(call) }
        post("/{id}/view")       { controller.viewStory(call) }
    }
}
