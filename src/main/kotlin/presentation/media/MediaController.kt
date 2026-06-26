package presentation.media

import core.utils.BadRequestException
import core.utils.success
import core.utils.userId
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.io.File
import java.util.UUID

class MediaController {

    private val uploadDir   = System.getenv("STORAGE_PATH") ?: "uploads/"
    private val maxSizeMB   = 50L
    private val maxSizeBytes = maxSizeMB * 1024 * 1024

    // POST /media/upload
    suspend fun uploadFile(call: ApplicationCall) {
        val userId  = call.userId()
        val multipart = call.receiveMultipart()

        var savedUrl: String? = null
        var mimeType: String? = null

        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                val originalName = part.originalFileName ?: "file"
                val ext          = originalName.substringAfterLast('.', "bin")
                val fileName     = "${UUID.randomUUID()}.$ext"
                val subDir       = when {
                    ext in listOf("jpg","jpeg","png","webp","gif") -> "images"
                    ext in listOf("mp4","mov","avi","mkv")         -> "videos"
                    ext in listOf("mp3","aac","ogg","opus","m4a")  -> "audio"
                    else                                            -> "docs"
                }
                val dir = File("$uploadDir/$subDir").also { it.mkdirs() }
                val file = File(dir, fileName)

                var totalBytes = 0L
                part.streamProvider().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            totalBytes += read
                            if (totalBytes > maxSizeBytes) {
                                file.delete()
                                throw BadRequestException("File exceeds max size of ${maxSizeMB}MB")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                mimeType  = part.contentType?.toString()
                savedUrl  = "/uploads/$subDir/$fileName"
            }
            part.dispose()
        }

        val url = savedUrl ?: throw BadRequestException("No file received")
        call.respond(HttpStatusCode.OK, success(UploadResponse(url, mimeType)))
    }
}

@Serializable
data class UploadResponse(val url: String, val mimeType: String?)

// ── Routes ────────────────────────────────────────────────────
fun Route.mediaRoutes() {
    val controller by inject<MediaController>()

    route("/media") {
        post("/upload") { controller.uploadFile(call) }
    }
}
