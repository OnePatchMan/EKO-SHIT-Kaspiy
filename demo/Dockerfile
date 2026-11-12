FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY . .
RUN chmod +x mvnw && ./mvnw -B -DskipTests package
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/target/demo-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
