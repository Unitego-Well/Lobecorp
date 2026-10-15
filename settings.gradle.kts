pluginManagement.repositories {
    mavenLocal()
    gradlePluginPortal()
    maven { url = uri("https://maven.neoforged.net/releases") }
    // mixinmcp_decompile
    maven { url = uri("https://maven.muon.rip/releases") }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":minecraft-mcp")
project(":minecraft-mcp").projectDir = file("modules/minecraft-mcp")
