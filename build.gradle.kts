plugins {
    kotlin("jvm") version "2.0.0"
    application
}

repositories { mavenCentral() }

dependencies {
    // Ktor
    val ktor = "2.3.12"
    implementation("io.ktor:ktor-server-core-jvm:$ktor")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor")
    implementation("io.ktor:ktor-serialization-jackson:$ktor")

    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Telegram API (lightweight HTTP only; no heavy framework)
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1")

    // DB (SQLite + exposed) — быстро стартануть, потом можно Postgres
    implementation("org.jetbrains.exposed:exposed-core:0.53.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.53.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.53.0")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")

    // dotenv (for local dev)
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    // Tests
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("app.MainKt")
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(17) }
