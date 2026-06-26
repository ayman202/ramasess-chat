# ── مرحلة البناء ──────────────────────────────────────────────
FROM gradle:8.11-jdk21 AS build
WORKDIR /app

# نسخ ملفات gradle أولاً (cache optimization)
COPY gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/

# تحميل التبعيات
RUN gradle dependencies --no-daemon || true

# نسخ الكود ثم البناء
COPY src/ src/
RUN gradle buildFatJar --no-daemon -x test

# ── مرحلة التشغيل (صورة أصغر) ────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# إنشاء مجلدات رفع الملفات
RUN mkdir -p uploads/images uploads/videos uploads/audio uploads/docs

# نسخ الـ JAR
COPY --from=build /app/build/libs/chat-backend-all.jar app.jar

EXPOSE 8080

# تشغيل السيرفر مع إعدادات JVM محسّنة
ENTRYPOINT ["java", \
  "-server", \
  "-XX:+UseG1GC", \
  "-XX:MaxRAMPercentage=75", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
