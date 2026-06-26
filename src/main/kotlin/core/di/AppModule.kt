package core.di

import core.security.EncryptionService
import core.security.JwtService
import core.security.OtpService
import presentation.auth.AuthController
import presentation.chat.ChatController
import presentation.contacts.ContactsController
import presentation.media.MediaController
import presentation.story.StoryController
import presentation.user.UserController
import data.repository.ContactRepositoryImpl
import data.repository.ConversationRepositoryImpl
import data.repository.MessageRepositoryImpl
import data.repository.StoryRepositoryImpl
import data.repository.UserRepositoryImpl
import domain.repository.ContactRepository
import domain.repository.ConversationRepository
import domain.repository.MessageRepository
import domain.repository.StoryRepository
import domain.repository.UserRepository
import domain.usecase.BlockContactUseCase
import domain.usecase.CreateConversationUseCase
import domain.usecase.CreateStoryUseCase
import domain.usecase.DeleteMessageUseCase
import domain.usecase.DeleteStoryUseCase
import domain.usecase.EditMessageUseCase
import domain.usecase.GetContactsUseCase
import domain.usecase.GetConversationsUseCase
import domain.usecase.GetMessagesUseCase
import domain.usecase.GetStoriesUseCase
import domain.usecase.GetUserProfileUseCase
import domain.usecase.LogoutUseCase
import domain.usecase.MuteConversationUseCase
import domain.usecase.PinConversationUseCase
import domain.usecase.ReactToMessageUseCase
import domain.usecase.RefreshTokenUseCase
import domain.usecase.SearchUsersUseCase
import domain.usecase.SendMessageUseCase
import domain.usecase.SendOtpUseCase
import domain.usecase.SetupProfileUseCase
import domain.usecase.SyncContactsUseCase
import domain.usecase.UpdateUserProfileUseCase
import domain.usecase.VerifyOtpUseCase
import domain.usecase.ViewStoryUseCase
import io.ktor.server.config.*
import org.koin.dsl.module


object AppModule {

    fun allModules(config: ApplicationConfig) = listOf(
        securityModule(config),
        repositoryModule,
        useCaseModule,
        controllerModule
    )

    // ── Security ──────────────────────────────────────────────
    private fun securityModule(config: ApplicationConfig) = module {
        single { JwtService(config) }
        single { EncryptionService(config) }
        single { OtpService(config, get()) }
    }

    // ── Repositories ──────────────────────────────────────────
    private val repositoryModule = module {
        single<UserRepository>         { UserRepositoryImpl() }
        single<ConversationRepository> { ConversationRepositoryImpl() }
        single<MessageRepository>      { MessageRepositoryImpl() }
        single<StoryRepository>        { StoryRepositoryImpl() }
        single<ContactRepository>      { ContactRepositoryImpl() }
    }

    // ── Use Cases ─────────────────────────────────────────────
    private val useCaseModule = module {
        // Auth
        factory { SendOtpUseCase(get(), get()) }
        factory { VerifyOtpUseCase(get(), get(), get()) }
        factory { RefreshTokenUseCase(get(), get()) }
        factory { SetupProfileUseCase(get()) }
        factory { LogoutUseCase(get()) }

        // User
        factory { GetUserProfileUseCase(get()) }
        factory { UpdateUserProfileUseCase(get()) }
        factory { SearchUsersUseCase(get()) }

        // Messages
        factory { SendMessageUseCase(get(), get()) }
        factory { GetMessagesUseCase(get(), get()) }
        factory { EditMessageUseCase(get()) }
        factory { DeleteMessageUseCase(get()) }
        factory { ReactToMessageUseCase(get()) }

        // Conversations
        factory { GetConversationsUseCase(get()) }
        factory { CreateConversationUseCase(get(), get()) }
        factory { PinConversationUseCase(get()) }
        factory { MuteConversationUseCase(get()) }

        // Stories
        factory { CreateStoryUseCase(get()) }
        factory { GetStoriesUseCase(get()) }
        factory { DeleteStoryUseCase(get()) }
        factory { ViewStoryUseCase(get()) }

        // Contacts
        factory { SyncContactsUseCase(get()) }
        factory { GetContactsUseCase(get()) }
        factory { BlockContactUseCase(get()) }
    }

    // ── Controllers ───────────────────────────────────────────
    private val controllerModule = module {
        single { AuthController(get(), get(), get(), get(), get()) }
        single { ChatController(get(), get(), get(), get(), get(), get()) }
        single { UserController(get(), get(), get()) }
        single { StoryController(get(), get(), get(), get()) }
        single { ContactsController(get(), get(), get()) }
        single { MediaController() }
    }
}
