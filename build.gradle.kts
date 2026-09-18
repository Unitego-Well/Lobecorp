@file:Suppress("AvoidDuplicateDependencies")

plugins {
    `java-library`
    `maven-publish`
    idea
    alias(libs.plugins.moddev)
    alias(libs.plugins.mixinmcpdecompile)
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
    mavenCentral()
    exclusiveContent {
        forRepository { maven { url = uri("https://maven.bawnorton.com/releases") } }
        filter { includeGroup("com.github.bawnorton.mixinsquared") }
    }
    exclusiveContent {
        forRepository { maven { url = uri("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/") } }
        filter { includeGroup("com.geckolib") }
    }
    exclusiveContent {
        forRepository { maven { url = uri("https://maven.theillusivec4.top/") } }
        filter { includeGroup("top.theillusivec4.curios") }
    }
    // jie
    maven { url = uri("https://modmaven.dev") }
    // jade reretrodamageindicators
    maven { url = uri("https://api.modrinth.com/maven") }
    // photon 依赖 (LDLib2 / KilaGraph)
    maven { url = uri("https://maven.firstdark.dev/snapshots") }
}

dependencies {
    implementation(libs.anvilcraftlib)
    jarJar(libs.mixinsquared)?.let { implementation(it) }
    implementation(libs.geckolib)
    interfaceInjectionData(libs.geckolib)
    implementation(libs.curios)
    implementation(libs.jeiapi)
    implementation(libs.jei)
    implementation(libs.jade)
    implementation(libs.reretrodamageindicators)
    // photon 依赖
    implementation(libs.ldlib2)
    implementation(libs.kilagraph)
    implementation(libs.photon){
        isTransitive = false
    }
    // lib/ 下的本地 jar (Photon) 打包进 mod 并加入 dev classpath
//    jarJar(fileTree("lib") { include("*.jar") })?.let { implementation(it) }
    implementation(fileTree("lib") { include("*.jar") })
}

base {
    archivesName = "$modId-$minecraftVersion"
}

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

neoForge {
    version = neoforgeVersion
    interfaceInjectionData.from("src/main/resources/META-INF/interfaces.json")
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
                "-DMC_DEBUG_ENABLED=true",
                "-DMC_DEBUG_PATHFINDING=true",
                "-DMC_DEBUG_GOAL_SELECTOR=true",
                "-DMC_DEBUG_SHOW_LOCAL_SERVER_ENTITY_HIT_BOXES=true",
                "-DMC_DEBUG_SHAPES=true",
                "-DMC_DEBUG_MONITOR_TICK_TIMES=true",
                "-XX:+IgnoreUnrecognizedVMOptions",
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
