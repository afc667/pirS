plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.sovereignty"
version = "2.0.0-SNAPSHOT"
description = "Sovereignty — A Hardcore RPG Claim Engine (Phase 2: Micro-SMP)"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")  // VaultAPI
}

dependencies {
    // PaperMC API — provided at runtime by the server
    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")

    // Vault Economy API — provided at runtime (soft dependency)
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")

    // HikariCP — high-performance JDBC connection pool
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Caffeine — high-performance caching library for O(1) chunk lookups
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("com.zaxxer.hikari", "com.sovereignty.libs.hikari")
    relocate("com.github.benmanes.caffeine", "com.sovereignty.libs.caffeine")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
