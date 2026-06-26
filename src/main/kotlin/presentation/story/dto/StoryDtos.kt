package presentation.story.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateStoryRequest(
    val mediaUrl: String,
    val mediaType: String  = "IMAGE",   // IMAGE | VIDEO
    val caption: String?   = null
)
