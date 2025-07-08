plugins {
    kotlin("jvm") version "2.1.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
    application
}

group = "fr.bearit.template"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // H2 Database
    implementation("com.h2database:h2:2.2.224")

    // Exposed (Kotlin SQL framework)
    implementation("org.jetbrains.exposed:exposed-core:0.46.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.46.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.46.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.46.0")

    // Compose dependencies
    val composeVersion = "1.8.2"
//    implementation("org.jetbrains.compose.desktop:desktop:$composeVersion")
    implementation("org.jetbrains.compose.desktop:desktop-jvm-macos-arm64:$composeVersion")
    implementation("org.jetbrains.compose.material:material:$composeVersion")
    implementation("org.jetbrains.compose.ui:ui:$composeVersion")
    implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
    implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

// Configure the main class for the application
application {
    mainClass.set("fr.bearit.template.ui.MinimalComposeAppKt")
}
