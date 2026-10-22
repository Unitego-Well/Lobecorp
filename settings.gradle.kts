pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven { url = uri("https://maven.neoforged.net/releases") }
        // mixinmcp_decompile
        maven { url = uri("https://maven.muon.rip/releases") }
        mavenCentral()
        maven("https://jitpack.io") {
            content { includeGroup("com.github.zhongbai2333.BenchMod") }
        }
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.zhongbai233.minecraft-bench") {
                useModule("com.github.zhongbai2333.BenchMod:bench-gradle-plugin:${requested.version}")
            }
        }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":minecraft-mcp")
project(":minecraft-mcp").projectDir = file("modules/minecraft-mcp")
