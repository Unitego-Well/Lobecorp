@file:Suppress("AvoidDuplicateDependencies")

import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.gradle.ext.Gradle
import org.jetbrains.gradle.ext.IdeaExtPlugin
import org.jetbrains.gradle.ext.ProjectSettings
import org.jetbrains.gradle.ext.RunConfigurationContainer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Properties

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

val minecraftMcpProject = project(":minecraft-mcp")
evaluationDependsOn(minecraftMcpProject.path)
val minecraftMcpSources = minecraftMcpProject.extensions.getByType<SourceSetContainer>().named("main").get()

val minecraftVersion = libs.versions.minecraft.get()
val neoforgeVersion = libs.versions.neoforge.get()
val mixinsquaredVersion = libs.versions.mixinsquared.get()
val modId = property("mod_id").toString()
val modVersion = property("mod_version").toString()
val buildDate: String = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
val buildCounterFile = layout.projectDirectory.file(".gradle/lobecorp-build-counter.properties").asFile
val previousBuildCounter = Properties().apply {
    if (buildCounterFile.isFile) {
        buildCounterFile.inputStream().use(::load)
    }
}
val buildCount = if (previousBuildCounter.getProperty("date") == buildDate) {
    previousBuildCounter.getProperty("count")?.toIntOrNull()?.plus(1) ?: 1
} else {
    1
}
val buildVersion = "$modVersion-$buildDate-$buildCount"
val reserveBuildIdentity = tasks.register("reserveBuildIdentity") {
    description = "Reserves the next local daily artifact build number"
    notCompatibleWithConfigurationCache("The daily build number is reserved in local persistent state")
    outputs.upToDateWhen { false }
    doLast {
        buildCounterFile.parentFile.mkdirs()
        val currentBuildCounter = Properties().apply {
            setProperty("date", buildDate)
            setProperty("count", buildCount.toString())
        }
        buildCounterFile.outputStream().use { currentBuildCounter.store(it, null) }
    }
}
val internalTestMods = configurations.create("internalTestMods") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
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
        forRepository { mavenCentral() }
        filter {
            includeGroup("org.junit")
            includeGroup("org.junit.jupiter")
            includeGroup("org.junit.platform")
            includeGroup("org.opentest4j")
            includeGroup("org.apiguardian")
            includeGroup("org.jspecify")
            includeModule("dev.vfyjxf", "taffy")
        }
    }
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
    exclusiveContent {
        forRepository { maven { url = uri("https://maven.firstdark.dev/snapshots") } }
        filter {
            includeGroup("com.lowdragmc.photon")
            includeGroup("com.lowdragmc.ldlib2")
            includeGroup("com.lowdragmc.kilagraph")
        }
    }
    maven { url = uri("https://maven.confluence.ink/releases") }
}

dependencies {
    compileOnly(project(":minecraft-mcp"))
    listOf("implementation", "runtimeOnly").forEach { configuration ->
        minecraftMcpProject.configurations[configuration].allDependencies.forEach { dependency ->
            runtimeOnly(dependency.copy())
        }
    }

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

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
    implementation(libs.particlestorm)
    // lib/ 下的本地 jar (Photon) 打包进模组并加入开发类路径
//    jarJar(fileTree("lib") { include("*.jar") })?.let { implementation(it) }
    implementation(fileTree("lib") { include("*.jar") })

    internalTestMods(libs.anvilcraftlib)
    internalTestMods(libs.geckolib)
    internalTestMods(libs.curios)
    internalTestMods(libs.jei)
    internalTestMods(libs.jade)
    internalTestMods(libs.reretrodamageindicators)
    internalTestMods(libs.ldlib2)
    internalTestMods(libs.kilagraph)
    internalTestMods(libs.photon)
    internalTestMods(libs.particlestorm)
    internalTestMods(fileTree("lib") { include("*.jar") })
}

base {
    archivesName = "$modId-$minecraftVersion"
}

tasks.named<Jar>("jar") {
    dependsOn(reserveBuildIdentity)
    archiveFileName = "$modId-$minecraftVersion-$buildVersion.jar"
    notCompatibleWithConfigurationCache("The archive name contains a local daily build number")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

tasks.test {
    useJUnitPlatform()
}

neoForge {
    version = neoforgeVersion
    addModdingDependenciesTo(sourceSets.test.get())
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
        register("mcpmod") {
            sourceSet(minecraftMcpSources)
        }
    }
    runs.configureEach {
        loadedMods.set(listOf(mods[modId]))
    }
    runs.named("client") {
        loadedMods.set(listOf(mods[modId], mods["mcpmod"]))
    }
}

tasks.processResources {
    val replaceProperties = mapOf(
        "minecraft_version" to minecraftVersion,
        "neoforge_version" to neoforgeVersion,
        "geckolib_version" to libs.versions.geckolib.get(),
        "curios_version" to libs.versions.curios.get(),
        "particlestorm_version" to libs.versions.particlestorm.get(),
        "photon_version" to libs.versions.photon.get(),
        "ldlib2_version" to libs.versions.ldlib2.get(),
        "kilagraph_version" to libs.versions.kilagraph.get(),
        "mod_id" to modId,
        "mod_version" to modVersion,
        "mod_name" to providers.gradleProperty("mod_name").get(),
        "mod_license" to providers.gradleProperty("mod_license").get(),
        "mod_update_json_url" to providers.gradleProperty("mod_update_json_url").get(),
        "mod_display_url" to providers.gradleProperty("mod_display_url").get(),
        "mod_issue_tracker_url" to providers.gradleProperty("mod_issue_tracker_url").get(),
        "mod_logo_file" to providers.gradleProperty("mod_logo_file").get(),
        "mod_credits" to providers.gradleProperty("mod_credits").get(),
        "mod_authors" to providers.gradleProperty("mod_authors").get(),
        "mod_description" to providers.gradleProperty("mod_description").get()
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

tasks.register<org.gradle.api.tasks.bundling.Zip>("internalTestBundle") {
    group = "distribution"
    description = "Builds the internal-test mod bundle with all runtime mod dependencies"
    dependsOn(tasks.named("jar"))
    archiveFileName = "$modId-$minecraftVersion-$buildVersion-internal-test.zip"
    notCompatibleWithConfigurationCache("The archive name contains a local daily build number")
    destinationDirectory = layout.buildDirectory.dir("distributions")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    into("mods") {
        from(tasks.named<Jar>("jar").flatMap { it.archiveFile })
        from(internalTestMods)
    }
    from(resources.text.fromString(
        """Lobotomy Corporation internal test bundle
Minecraft: $minecraftVersion
NeoForge: $neoforgeVersion
Mod version: $modVersion

Install Minecraft $minecraftVersion with NeoForge $neoforgeVersion, then copy every file from mods into the instance mods directory.
"""
    )) {
        rename { "README.txt" }
    }
}

plugins.withType<IdeaExtPlugin> {
    val projectSettings = (idea.project as ExtensionAware).extensions.getByType<ProjectSettings>()
    val runConfigurations = (projectSettings as ExtensionAware).extensions.getByType<RunConfigurationContainer>()
    runConfigurations.create("Minecraft MCP Bridge", Gradle::class.java).apply {
        projectPath = rootDir.absolutePath
        taskNames = listOf(":minecraft-mcp:checkBridge", ":minecraft-mcp:buildBridge", ":minecraft-mcp:probeBridge")
    }
    runConfigurations.create("Minecraft MCP Effect Runtime", Gradle::class.java).apply {
        projectPath = rootDir.absolutePath
        taskNames = listOf(":minecraft-mcp:probeEffectRuntime")
    }
    runConfigurations.create("Minecraft MCP Effect Authoring", Gradle::class.java).apply {
        projectPath = rootDir.absolutePath
        taskNames = listOf(":minecraft-mcp:probeEffectAuthoring")
    }
    listOf(
        "Minecraft MCP Reference Inspect" to ":minecraft-mcp:inspectReferenceEffects",
        "Minecraft MCP Reference Create" to ":minecraft-mcp:createReferenceEffects",
        "Minecraft MCP Reference Save" to ":minecraft-mcp:saveReferenceEffects",
    ).forEach { (name, task) ->
        runConfigurations.create(name, Gradle::class.java).apply {
            projectPath = rootDir.absolutePath
            taskNames = listOf(task)
        }
    }
    runConfigurations.create("Minecraft MCP Import Assets", Gradle::class.java).apply {
        projectPath = rootDir.absolutePath
        taskNames = listOf(":minecraft-mcp:importIcons")
    }
}

idea.module {
    isDownloadSources = true
    isDownloadJavadoc = true
}
