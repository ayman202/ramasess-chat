package presentation.media

import core.utils.BadRequestException
import core.utils.successResponse
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.json.put
import org.koin.ktor.ext.inject
import java.io.File
import java.util.UUID

class MediaController {

    private val uploadDir    = System.getenv("STORAGE_PATH") ?: "uploads/"
    private val maxSizeBytes = 50L * 1024 * 1024   // 50 MB

    suspend fun uploadFile(call: ApplicationCall) {
        val multipart = call.receiveMultipart()
        var savedUrl: String? = null
        var mimeType: String? = null

        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                val ext    = part.originalFileName
                    ?.substringAfterLast('.', "bin") ?: "bin"
                val subDir = when (ext.lowercase()) {
                    in listOf("jpg", "jpeg", "png", "webp", "gif") -> "images"
                    in listOf("mp4", "mov", "avi", "mkv")          -> "videos"
                    in listOf("mp3", "aac", "ogg", "opus", "m4a")  -> "audio"
                    else                                             -> "docs"
                }

                val dir      = File("$uploadDir/$subDir").also { it.mkdirs() }
                val fileName = "${UUID.randomUUID()}.$ext"
                val file     = File(dir, fileName)

                // ✅ قراءة البيانات كـ ByteArray
                val bytes = part.provider().toByteArray()

                if (bytes.size.toLong() > maxSizeBytes) {
                    throw BadRequestException("File exceeds 50MB limit")
                }

                file.writeBytes(bytes)

                mimeType = part.contentType?.toString()
                savedUrl = "/uploads/$subDir/$fileName"
            }
            part.dispose()
        }

        val url = savedUrl ?: throw BadRequestException("No file received")

        call.respond(HttpStatusCode.OK, successResponse {
            put("url",      url)
            put("mimeType", mimeType)
        })
    }
}

fun Route.mediaRoutes() {
    val controller by inject<MediaController>()
    route("/media") {
        post("/upload") { controller.uploadFile(call) }
    }
}

