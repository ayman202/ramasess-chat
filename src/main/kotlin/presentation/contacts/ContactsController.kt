package presentation.contacts

import core.utils.BadRequestException
import core.utils.successResponse
import core.utils.userId
import domain.usecase.BlockContactUseCase
import domain.usecase.GetContactsUseCase
import domain.usecase.SyncContactsUseCase
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.koin.ktor.ext.inject
import presentation.contacts.dto.BlockContactRequest
import presentation.contacts.dto.SyncContactsRequest

class ContactsController(
    private val syncContactsUseCase: SyncContactsUseCase,
    private val getContactsUseCase: GetContactsUseCase,
    private val blockContactUseCase: BlockContactUseCase
) {

    // GET /contacts
    suspend fun getContacts(call: ApplicationCall) {
        val contacts = getContactsUseCase(call.userId())
        call.respond(HttpStatusCode.OK, successResponse {
            putJsonArray("contacts") {
                contacts.forEach { contact ->
                    addJsonObject {
                        put("id",              contact.id)
                        put("contactUserId",   contact.contactUserId)
                        put("name",            contact.name)
                        put("phone",           contact.phone)
                        put("profileImageUrl", contact.profileImageUrl)
                        put("isOnline",        contact.isOnline)
                        put("lastSeen",        contact.lastSeen)
                        put("isBlocked",       contact.isBlocked)
                        put("addedAt",         contact.addedAt)
                    }
                }
            }
        })
    }

    // POST /contacts/sync
    suspend fun syncContacts(call: ApplicationCall) {
        val req      = call.receive<SyncContactsRequest>()
        val contacts = syncContactsUseCase(call.userId(), req.phones)
        call.respond(HttpStatusCode.OK, successResponse {
            put("synced", contacts.size)
            putJsonArray("contacts") {
                contacts.forEach { contact ->
                    addJsonObject {
                        put("id",              contact.id)
                        put("contactUserId",   contact.contactUserId)
                        put("name",            contact.name)
                        put("phone",           contact.phone)
                        put("profileImageUrl", contact.profileImageUrl)
                        put("isOnline",        contact.isOnline)
                        put("isBlocked",       contact.isBlocked)
                    }
                }
            }
        })
    }

    // POST /contacts/{contactId}/block
    suspend fun blockOrUnblock(call: ApplicationCall) {
        val userId    = call.userId()
        val contactId = call.parameters["contactId"]
            ?: throw BadRequestException("Missing contactId")
        val req = call.receive<BlockContactRequest>()

        if (req.block) {
            blockContactUseCase.block(userId, contactId)
        } else {
            blockContactUseCase.unblock(userId, contactId)
        }

        call.respond(HttpStatusCode.OK, successResponse {
            put("blocked", req.block)
        })
    }
}

fun Route.contactsRoutes() {
    val controller by inject<ContactsController>()
    route("/contacts") {
        get            { controller.getContacts(call) }
        post("/sync")  { controller.syncContacts(call) }
        post("/{contactId}/block") { controller.blockOrUnblock(call) }
    }
}
