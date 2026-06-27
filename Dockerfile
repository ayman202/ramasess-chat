FROM gradle:8.11-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle buildFatJar --no-daemon -x test

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# ✅ * بدل اسم ثابت — يجيب أي jar موجود
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
