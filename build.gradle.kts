import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test

plugins {
    java
    id("org.jetbrains.intellij") version "1.15.0"
}

group = "com.tutor"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
}

dependencies {
    implementation("com.google.genai:google-genai:1.51.0")
    implementation("io.modelcontextprotocol.sdk:mcp:1.1.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.3")
    compileOnly("org.jetbrains:annotations:24.0.0")
}

intellij {
    version.set("2026.1")
    type.set("IC")
    plugins.set(listOf("java"))
}

tasks {
    withType<JavaCompile> {
        options.release.set(25)
        options.compilerArgs.add("--enable-preview")
    }

    withType<Test> {
        useJUnitPlatform()
        jvmArgs("--enable-preview")
        options.compilerArgs.add("--enable-preview")
    }

    patchPluginXml {
        sinceBuild.set("261")
        untilBuild.set("262.*")
    }

    runIde {
        jvmArgs("--enable-preview")
    }

    buildSearchableOptions {
        enabled = false
    }
}
