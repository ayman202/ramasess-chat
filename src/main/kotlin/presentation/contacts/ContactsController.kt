package presentation.contacts

import core.utils.BadRequestException
import core.utils.success
import core.utils.userId
import domain.usecase.BlockContactUseCase
import domain.usecase.GetContactsUseCase
import domain.usecase.SyncContactsUseCase
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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
        call.respond(HttpStatusCode.OK, success(contacts))
    }

    // POST /contacts/sync
    // العميل يرسل قائمة أرقام الهاتف → السيرفر يرجّع المستخدمين المسجلين
    suspend fun syncContacts(call: ApplicationCall) {
        val req      = call.receive<SyncContactsRequest>()
        val contacts = syncContactsUseCase(call.userId(), req.phones)
        call.respond(HttpStatusCode.OK, success(contacts, "Synced ${contacts.size} contacts"))
    }

    // POST /contacts/{contactId}/block
    suspend fun blockOrUnblock(call: ApplicationCall) {
        val userId    = call.userId()
        val contactId = call.parameters["contactId"]
            ?: throw BadRequestException("Missing contactId")
        val req       = call.receive<BlockContactRequest>()

        if (req.block) {
            blockContactUseCase.block(userId, contactId)
            call.respond(HttpStatusCode.OK, success(mapOf("blocked" to true)))
        } else {
            blockContactUseCase.unblock(userId, contactId)
            call.respond(HttpStatusCode.OK, success(mapOf("blocked" to false)))
        }
    }
}

// ── Routes ────────────────────────────────────────────────────
fun Route.contactsRoutes() {
    val controller by inject<ContactsController>()

    route("/contacts") {
        get              { controller.getContacts(call) }
        post("/sync")    { controller.syncContacts(call) }
        post("/{contactId}/block") { controller.blockOrUnblock(call) }
    }
}
