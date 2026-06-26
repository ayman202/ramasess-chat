package presentation.chat

import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.chatRoutes() {
    val controller by inject<ChatController>()

    // ── Conversations ─────────────────────────────────────────
    route("/conversations") {

        get  { controller.getConversations(call) }

        post("/private") { controller.createPrivateConversation(call) }
        post("/group")   { controller.createGroupConversation(call) }

        route("/{id}") {

            // Messages
            route("/messages") {
                get              { controller.getMessages(call) }
                post             { controller.sendMessage(call) }

                route("/{msgId}") {
                    put    { controller.editMessage(call) }
                    delete { controller.deleteMessage(call) }
                    post("/react") { controller.reactToMessage(call) }
                }

                // بحث في الرسائل
                get("/search") { controller.searchMessages(call) }
            }

            // Pin / Mute
            post("/pin")  { controller.pinConversation(call) }
            post("/mute") { controller.muteConversation(call) }
        }
    }
}
