import core.config.DatabaseConfig
import core.di.AppModule
import core.plugins.configureCompression
import core.plugins.configureCors
import core.plugins.configureMonitoring
import core.plugins.configureRateLimiting
import core.plugins.configureRouting
import core.plugins.configureSecurity
import core.plugins.configureSerialization
import core.plugins.configureStatusPages
import core.plugins.configureWebSockets
import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

// ✅ نقطة البداية - Ktor يستدعيها من application.conf
fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {

    // 1️⃣ Koin - Dependency Injection
    install(Koin) {
        slf4jLogger()
        modules(AppModule.allModules(environment.config))
    }

    // 2️⃣ قاعدة البيانات - إنشاء الجداول
    DatabaseConfig.init(environment.config)

    // 3️⃣ Plugins بالترتيب الصحيح
    configureMonitoring()       // logging & call tracing
    configureSerialization()    // JSON
    configureCompression()      // gzip
    configureCors()             // CORS headers
    configureSecurity()         // JWT
    configureWebSockets()       // WS
    configureRateLimiting()     // anti-spam
    configureStatusPages()      // error handling
    configureRouting()          // all routes
}
