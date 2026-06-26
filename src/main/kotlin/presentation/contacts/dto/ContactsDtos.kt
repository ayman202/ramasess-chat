package presentation.contacts.dto

import kotlinx.serialization.Serializable

@Serializable
data class SyncContactsRequest(val phones: List<String>)

@Serializable
data class BlockContactRequest(val block: Boolean)   // true=block, false=unblock
