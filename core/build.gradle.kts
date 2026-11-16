plugins {
    kotlin("jvm") version "2.1.21"
}

group = "com.pocketpilot"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Thin HTTP client for the OpenAI-compatible LLM endpoint.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Small JSON library (stable API, no Android dependency).
    implementation("org.json:json:20250517")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}

/** Run the JVM demo: scripted model + fake screen, no Android, no network. */
tasks.register<JavaExec>("runDemo") {
    group = "application"
    mainClass.set("com.pocketpilot.core.DemoMain")
    classpath = sourceSets["main"].runtimeClasspath
}
