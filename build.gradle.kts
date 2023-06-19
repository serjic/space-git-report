val jackson_version: String by project
val log4j_version: String by project
val ktor_version: String by project
val kotlin_version: String by project
val kotlin_datetime_version: String by project
val logback_version: String by project
val space_client_version: String by project
val datetime_version: String by project
val okhttp_version: String by project

plugins {
    kotlin("multiplatform") version "1.8.0"
    id("maven-publish")
    id("io.ktor.plugin") version "2.3.1"
}

application {
    mainClass.set("space.jetbrains.git-report")
}

ktor {
    docker {
        localImageName.set("space-git-report")

        imageTag.set("0.1.6-preview")

        externalRegistry.set(
            io.ktor.plugin.features.DockerImageRegistry.dockerHub(
                appName = provider { "space-git-report" },
                username = providers.gradleProperty("dockerhubUsername"),
                password = providers.gradleProperty("dockerhubAccessToken")
            )
        )
    }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/space/maven")
}

kotlin {
    jvm {
        jvmToolchain(11)
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${jackson_version}")
                implementation("io.ktor:ktor-client-core:${ktor_version}")
                implementation("io.ktor:ktor-client-apache:${ktor_version}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${kotlin_datetime_version}")
                implementation("ch.qos.logback:logback-classic:${logback_version}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${kotlin_datetime_version}")

                implementation("org.jetbrains:space-sdk:$space_client_version")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:$datetime_version")

                implementation("io.ktor:ktor-server-caching-headers-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-compression-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-cors-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
                implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-default-headers:$ktor_version")
                implementation("io.ktor:ktor-server-call-logging:$ktor_version")
                implementation("io.ktor:ktor-client-core:$ktor_version")
                implementation("io.ktor:ktor-client-apache:$ktor_version")
                implementation("ch.qos.logback:logback-classic:$logback_version")

            }
        }
    }
}
