package data.repository

import domain.repository.ContactRepository
import domain.repository.StoryRepository
import data.database.tables.ContactsTable
import data.database.tables.StoriesTable
import data.database.tables.StoryViewsTable
import data.database.tables.UsersTable
import domain.model.Contact
import domain.model.Story
import domain.model.StoryMediaType
import domain.model.StoryViewer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import java.util.UUID

// ─────────────────────────────────────────────────────────────
// StoryRepositoryImpl
// ─────────────────────────────────────────────────────────────
class StoryRepositoryImpl : StoryRepository {

    private fun toStory(
        row: ResultRow,
        viewers: List<StoryViewer>,
        isViewed: Boolean
    ) = Story(
        id = row[StoriesTable.id].toString(),
        userId = row[StoriesTable.userId].toString(),
        userName = row.getOrNull(UsersTable.name),
        userAvatar = row.getOrNull(UsersTable.profileImageUrl),
        mediaUrl = row[StoriesTable.mediaUrl],
        mediaType = StoryMediaType.valueOf(row[StoriesTable.mediaType]),
        caption = row[StoriesTable.caption],
        viewsCount = row[StoriesTable.viewsCount],
        viewers = viewers,
        isViewed = isViewed,
        createdAt = row[StoriesTable.createdAt].toString(),
        expiresAt = row[StoriesTable.expiresAt].toString()
    )

    override suspend fun createStory(
        userId: String,
        mediaUrl: String,
        mediaType: StoryMediaType,
        caption: String?
    ): Story = newSuspendedTransaction {
        val expiresAt = LocalDateTime.now().plusHours(24)
        val id = StoriesTable.insert {
            it[StoriesTable.userId]    = UUID.fromString(userId)
            it[StoriesTable.mediaUrl]  = mediaUrl
            it[StoriesTable.mediaType] = mediaType.name
            it[StoriesTable.caption]   = caption
            it[StoriesTable.expiresAt] = expiresAt
        } get StoriesTable.id

        val row = (StoriesTable leftJoin UsersTable)
            .selectAll().where { StoriesTable.id eq id }.single()
        toStory(row, emptyList(), false)
    }

    override suspend fun getActiveStoriesForUser(viewerId: String): List<Story> =
        newSuspendedTransaction {
            val uid = UUID.fromString(viewerId)
            val now = LocalDateTime.now()

            // جلب IDs الأشخاص اللي شايفهم (contacts + نفسه)
            val contactIds = ContactsTable.selectAll()
                .where { (ContactsTable.userId eq uid) and (ContactsTable.isBlocked eq false) }
                .map { it[ContactsTable.contactUserId] }
                .toMutableList()
            contactIds.add(uid)

            val viewedIds = StoryViewsTable.selectAll()
                .where { StoryViewsTable.viewerId eq uid }
                .map { it[StoryViewsTable.storyId] }.toSet()

            (StoriesTable leftJoin UsersTable)
                .selectAll()
                .where {
                    (StoriesTable.userId inList contactIds) and
                    (StoriesTable.expiresAt greater now)
                }
                .orderBy(StoriesTable.createdAt, SortOrder.DESC)
                .map { row ->
                    val storyId   = row[StoriesTable.id]
                    val viewers   = loadViewers(storyId)
                    val isViewed  = storyId in viewedIds
                    toStory(row, viewers, isViewed)
                }
        }

    override suspend fun getUserStories(userId: String): List<Story> =
        newSuspendedTransaction {
            val uid = UUID.fromString(userId)
            val now = LocalDateTime.now()
            (StoriesTable leftJoin UsersTable)
                .selectAll()
                .where { (StoriesTable.userId eq uid) and (StoriesTable.expiresAt greater now) }
                .orderBy(StoriesTable.createdAt, SortOrder.DESC)
                .map { row ->
                    val viewers = loadViewers(row[StoriesTable.id])
                    toStory(row, viewers, false)
                }
        }

    private suspend fun loadViewers(storyId: UUID): List<StoryViewer> =
        newSuspendedTransaction {
            (StoryViewsTable innerJoin UsersTable)
                .selectAll()
                .where { StoryViewsTable.storyId eq storyId }
                .orderBy(StoryViewsTable.viewedAt, SortOrder.DESC)
                .map { r ->
                    StoryViewer(
                        userId = r[UsersTable.id].toString(),
                        userName = r[UsersTable.name],
                        viewedAt = r[StoryViewsTable.viewedAt].toString()
                    )
                }
        }

    override suspend fun addView(storyId: String, viewerId: String) =
        newSuspendedTransaction {
            val sid = UUID.fromString(storyId)
            val vid = UUID.fromString(viewerId)
            val alreadyViewed = StoryViewsTable.selectAll().where {
                (StoryViewsTable.storyId eq sid) and (StoryViewsTable.viewerId eq vid)
            }.count() > 0

            if (!alreadyViewed) {
                StoryViewsTable.insert {
                    it[StoryViewsTable.storyId]  = sid
                    it[StoryViewsTable.viewerId] = vid
                }
                StoriesTable.update({ StoriesTable.id eq sid }) {
                    with(SqlExpressionBuilder) {
                        it.update(viewsCount, viewsCount + 1)
                    }
                }
            }
            Unit
        }

    override suspend fun deleteStory(storyId: String, userId: String): Boolean =
        newSuspendedTransaction {
            val deleted = StoriesTable.deleteWhere {
                (id eq UUID.fromString(storyId)) and (StoriesTable.userId eq UUID.fromString(userId))
            }
            deleted > 0
        }

    override suspend fun deleteExpiredStories() =
        newSuspendedTransaction {
            StoriesTable.deleteWhere { expiresAt less LocalDateTime.now() }
            Unit
        }
}

// ─────────────────────────────────────────────────────────────
// ContactRepositoryImpl
// ─────────────────────────────────────────────────────────────
class ContactRepositoryImpl : ContactRepository {

    private fun toContact(row: ResultRow, userId: String) = Contact(
        id = row[ContactsTable.contactUserId].toString(),
        userId = userId,
        contactUserId = row[ContactsTable.contactUserId].toString(),
        name = row.getOrNull(UsersTable.name),
        phone = row[UsersTable.phone],
        profileImageUrl = row.getOrNull(UsersTable.profileImageUrl),
        isOnline = row[UsersTable.isOnline],
        lastSeen = row[UsersTable.lastSeen].toString(),
        isBlocked = row[ContactsTable.isBlocked],
        addedAt = row[ContactsTable.addedAt].toString()
    )

    override suspend fun syncContacts(userId: String, phones: List<String>): List<Contact> =
        newSuspendedTransaction {
            val uid = UUID.fromString(userId)

            // جلب المستخدمين اللي عندهم هذه الأرقام
            val foundUsers = UsersTable.selectAll()
                .where { (UsersTable.phone inList phones) and (UsersTable.id neq uid) }
                .toList()

            foundUsers.forEach { userRow ->
                val contactUid = userRow[UsersTable.id]
                val exists = ContactsTable.selectAll().where {
                    (ContactsTable.userId eq uid) and (ContactsTable.contactUserId eq contactUid)
                }.count() > 0

                if (!exists) {
                    ContactsTable.insert {
                        it[ContactsTable.userId]        = uid
                        it[ContactsTable.contactUserId] = contactUid
                    }
                }
            }

            // رجّع الـ contacts المحدّثة
            (ContactsTable innerJoin UsersTable)
                .selectAll()
                .where { ContactsTable.userId eq uid }
                .map { toContact(it, userId) }
        }

    override suspend fun getUserContacts(userId: String): List<Contact> =
        newSuspendedTransaction {
            val uid = UUID.fromString(userId)
            (ContactsTable innerJoin UsersTable)
                .selectAll()
                .where { ContactsTable.userId eq uid }
                .orderBy(UsersTable.name, SortOrder.ASC)
                .map { toContact(it, userId) }
        }

    override suspend fun blockContact(userId: String, contactUserId: String) =
        newSuspendedTransaction {
            ContactsTable.update({
                (ContactsTable.userId eq UUID.fromString(userId)) and
                (ContactsTable.contactUserId eq UUID.fromString(contactUserId))
            }) { it[isBlocked] = true }
            Unit
        }

    override suspend fun unblockContact(userId: String, contactUserId: String) =
        newSuspendedTransaction {
            ContactsTable.update({
                (ContactsTable.userId eq UUID.fromString(userId)) and
                (ContactsTable.contactUserId eq UUID.fromString(contactUserId))
            }) { it[isBlocked] = false }
            Unit
        }

    override suspend fun isBlocked(userId: String, contactUserId: String): Boolean =
        newSuspendedTransaction {
            ContactsTable.selectAll().where {
                (ContactsTable.userId eq UUID.fromString(userId)) and
                (ContactsTable.contactUserId eq UUID.fromString(contactUserId)) and
                (ContactsTable.isBlocked eq true)
            }.count() > 0
        }

    override suspend fun getContactPhones(userId: String): List<String> =
        newSuspendedTransaction {
            val uid = UUID.fromString(userId)
            (ContactsTable innerJoin UsersTable)
                .selectAll()
                .where { ContactsTable.userId eq uid }
                .map { it[UsersTable.phone] }
        }
}
