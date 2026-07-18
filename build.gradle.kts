plugins {
    `java-library`
    `maven-publish`
    idea
    alias(libs.plugins.moddev)
}
tasks.wrapper {
    distributionType = Wrapper.DistributionType.BIN
}
val minecraftVersion = libs.versions.minecraft.get()
val neoforgeVersion = libs.versions.neoforge.get()
val mixinsquaredVersion = libs.versions.mixinsquared.get()
val modId = property("mod_id").toString()
val modVersion = property("mod_version").toString()

version = modVersion
group = "org.unitego.lobecorp"

sourceSets.main {
    resources {
        srcDir("src/generated/resources")
        exclude("src/generated/**/.cache")
    }
}
repositories {
    mavenLocal()
    // NeoForge
    maven { url = uri("https://neoforged.forgecdn.net/releases") }

    mavenCentral()

    // geckolib
    exclusiveContent {
        forRepository {
            maven {
                url = uri("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
            }
        }
        filter {
            includeGroup("com.geckolib")
        }
    }

    // curios
    exclusiveContent {
        forRepository {
            maven {
                url = uri("https://maven.theillusivec4.top/")
            }
        }
        filter {
            includeGroup("top.theillusivec4.curios")
        }
    }

    // mixinsquared
    exclusiveContent {
        forRepository {
            maven {
                url = uri("https://maven.bawnorton.com/releases")
            }
        }
        filter {
            includeGroup("com.github.bawnorton.mixinsquared")
        }
    }
}
dependencies {
    compileOnly(annotationProcessor("com.github.bawnorton.mixinsquared:mixinsquared-common:$mixinsquaredVersion")!!)
    implementation(jarJar("com.github.bawnorton.mixinsquared:mixinsquared-neoforge:$mixinsquaredVersion")!!)
    implementation(libs.geckolib)
    implementation(libs.curios)
}
base {
    archivesName = "$modId-$minecraftVersion"
}
java.toolchain.languageVersion = JavaLanguageVersion.of(25)
neoForge {
    version = neoforgeVersion
    setAccessTransformers("src/main/resources/META-INF/accesstransformer.cfg")
    runs {
        register("client") {
            client()
            gameDirectory = file("run/client")
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }
        register("server") {
            server()
            gameDirectory = file("run/server")
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }
        register("data") {
            clientData()
            gameDirectory = file("run/data")
            programArguments.addAll(
                "--mod", modId,
                "--all",
                "--output", file("src/generated/resources").absolutePath,
                "--existing", file("src/main/resources/").absolutePath
            )
        }
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
            jvmArguments.addAll(
                "-Dterminal.ansi=true",
                // 忽略无效指令，避免有的人没安装 JetBrain Runtime 无法启动游戏
                "-XX:+IgnoreUnrecognizedVMOptions",
                // 启用 JetBrain Runtime 热重载功能
                "-XX:+AllowEnhancedClassRedefinition"
            )
        }
    }
    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}
tasks.processResources {
    val replaceProperties = mapOf(
        "minecraft_version" to minecraftVersion,
        "neoforge_version" to neoforgeVersion,
        "geckolib_version" to libs.versions.geckolib.get(),
        "curios_version" to libs.versions.curios.get(),
        "mod_id" to modId,
        "mod_version" to modVersion
    )
    inputs.properties(replaceProperties)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(replaceProperties)
    }
}
publishing {
    publications.register<MavenPublication>("mavenJava") {
        from(components["java"])
    }
    repositories.maven {
        url = uri(layout.projectDirectory.dir("repo"))
    }
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
idea.module {
    isDownloadSources = true
    isDownloadJavadoc = true
}
