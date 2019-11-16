import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.50"
}

group = "com.kiko"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    testImplementation(kotlin("test-testng"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

tasks.test {
    useTestNG()
}

tasks.jar {
    manifest {
        // Make jar runnable.
        attributes["Main-Class"] = "com.kiko.flatviewingscheduler.FlatViewingSchedulerServerKt"
    }

    // Include Kotlin's runtime to the final jar.
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
