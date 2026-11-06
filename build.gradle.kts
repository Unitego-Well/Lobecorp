@file:Suppress("AvoidDuplicateDependencies")

import com.zhongbai233.bench.gradle.VerifyBenchReportTask
import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.gradle.ext.Application
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
    alias(libs.plugins.minecraftbench)
}

tasks.wrapper {
    distributionType = Wrapper.DistributionType.BIN
}

val photonBenchEnabled = providers.gradleProperty("photonBench").map(String::toBoolean).orElse(false)
val minecraftMcpJar = files("lib/minecraft-mcp-0.3.0.jar")
val minecraftMcpBridge = layout.projectDirectory.dir("lib/minecraft-mcp/bridge")
val minecraftMcpGamePort = providers.environmentVariable("MC_MCP_PORT").orElse(
    providers.fileContents(layout.projectDirectory.file(".codex/config.toml")).asText.map { config ->
        Regex("""(?m)^MC_MCP_PORT\s*=\s*"(\d+)"""").find(config)?.groupValues?.get(1)
            ?: error("Configure an explicit workspace MC_MCP_PORT")
    }
)

val minecraftMcpClient = sourceSets.create("mcpClient") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + minecraftMcpJar
}

val photonBenchClient = sourceSets.create("photonBenchClient") {
    runtimeClasspath += sourceSets["bench"].runtimeClasspath + minecraftMcpJar
}

// 普通 JVM 验证不经过游戏的嵌套 JAR 加载器，复用当前编译类路径的同版 MixinExtras。
val mixinExtrasCheckRuntime = sourceSets.main.get().compileClasspath.filter {
    it.name.startsWith("mixinextras-")
}
sourceSets.test {
    runtimeClasspath += mixinExtrasCheckRuntime
}
sourceSets.named("bench") {
    runtimeClasspath += mixinExtrasCheckRuntime
}
// 游戏仍由原启动器提供此类库，检查专用补充不进入 FULL 游戏类路径。
photonBenchClient.runtimeClasspath = photonBenchClient.runtimeClasspath - mixinExtrasCheckRuntime

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
    val reservedFile = buildCounterFile
    val reservedDate = buildDate
    val reservedCount = buildCount.toString()
    doLast {
        reservedFile.parentFile.mkdirs()
        val currentBuildCounter = Properties().apply {
            setProperty("date", reservedDate)
            setProperty("count", reservedCount)
        }
        reservedFile.outputStream().use { currentBuildCounter.store(it, null) }
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
        exclude("**/*.fxproj", "**/*.mfxproj", "**/*.zip", "**/*.bbmodel")
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
    exclusiveContent {
        forRepository { maven("https://jitpack.io") }
        filter { includeGroup("com.github.zhongbai2333.BenchMod") }
    }
}

dependencies {
    compileOnly(minecraftMcpJar)
    if (photonBenchEnabled.get()) {
        add("benchRuntimeMod", minecraftMcpJar)
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
    implementation(fileTree("lib") { include("*.jar"); exclude("minecraft-mcp-*.jar") })

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
    internalTestMods(fileTree("lib") { include("*.jar"); exclude("minecraft-mcp-*.jar") })
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
            if (name == "benchClient" && photonBenchEnabled.get()) {
                systemProperty("mcp.port", minecraftMcpGamePort.get())
            }
            if (!name.startsWith("bench")) {
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
    }
    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
    runs.configureEach {
        loadedMods.set(listOf(mods[modId]))
    }
    runs.named("client") {
        sourceSet.set(minecraftMcpClient)
        systemProperty("mcp.port", minecraftMcpGamePort.get())
    }
}

modBench {
    targetMod = modId
    expectedProviderCount = 1
    seed = 7
    jfrEnabled = true
    serverLevelType = "minecraft:flat"
    clientWorldPreset = "flat"
    clientWorldId = "lobecorp-bench"
    clientRequireWindowFocus = providers.gradleProperty("photonRequireFocus").map(String::toBoolean).orElse(true).get()
    if (photonBenchEnabled.get()) {
        scenarioFilter = providers.gradleProperty("photonScenarios").orElse("lobecorp.photon-*").get()
    }
}

tasks.named<VerifyBenchReportTask>("verifyBenchServerReport") {
    expectedScenarioIds.set(listOf("lobecorp.sweeper-ai"))
    expectedMetricNames.set(listOf("server.tick.duration", "lobecorp.sweeper_ai.loaded_entities"))
    expectedLoadedModIds.set(listOf("minecraft", "neoforge", modId, "modbench_runtime"))
    expectedArtifactPaths.set(
        listOf(
            "artifacts/samples/lobecorp.sweeper-ai.jsonl",
            "artifacts/jfr/lobecorp.sweeper-ai.jfr"
        )
    )
}

tasks.named<VerifyBenchReportTask>("verifyBenchClientReport") {
    if (photonBenchEnabled.get()) {
        expectedScenarioIds.set(
            listOf(
                "lobecorp.photon-00-empty",
                "lobecorp.photon-10-static-one",
                "lobecorp.photon-20-animated-one",
                "lobecorp.photon-30-static-sixteen",
                "lobecorp.photon-40-animated-sixteen",
                "lobecorp.photon-50-cold-sixteen",
                "lobecorp.photon-60-static-repeat",
                "lobecorp.photon-70-empty-repeat",
                "lobecorp.photon-80-static-thirtytwo",
                "lobecorp.photon-90-animated-thirtytwo",
                "lobecorp.photon-91-mixed-thirtytwo-laser",
                "lobecorp.photon-92-mixed-sixtyfour-four-lasers",
                "lobecorp.photon-99-empty-extreme-repeat"
            )
        )
        expectedMetricNames.set(listOf("client.frame.interval", "lobecorp.photon.loaded_models"))
        expectedLoadedModIds.set(listOf("minecraft", "neoforge", modId, "modbench_runtime", "sodium", "mcpmod"))
        expectedArtifactPaths.set(
            listOf(
                "artifacts/samples/lobecorp.photon-00-empty.jsonl",
                "artifacts/jfr/lobecorp.photon-00-empty.jfr",
                "artifacts/custom/photon-00-empty-workload.json",
                "artifacts/samples/lobecorp.photon-10-static-one.jsonl",
                "artifacts/jfr/lobecorp.photon-10-static-one.jfr",
                "artifacts/custom/photon-10-static-one-workload.json",
                "artifacts/samples/lobecorp.photon-20-animated-one.jsonl",
                "artifacts/jfr/lobecorp.photon-20-animated-one.jfr",
                "artifacts/custom/photon-20-animated-one-workload.json",
                "artifacts/samples/lobecorp.photon-30-static-sixteen.jsonl",
                "artifacts/jfr/lobecorp.photon-30-static-sixteen.jfr",
                "artifacts/custom/photon-30-static-sixteen-workload.json",
                "artifacts/samples/lobecorp.photon-40-animated-sixteen.jsonl",
                "artifacts/jfr/lobecorp.photon-40-animated-sixteen.jfr",
                "artifacts/custom/photon-40-animated-sixteen-workload.json",
                "artifacts/samples/lobecorp.photon-50-cold-sixteen.jsonl",
                "artifacts/jfr/lobecorp.photon-50-cold-sixteen.jfr",
                "artifacts/custom/photon-50-cold-sixteen-workload.json",
                "artifacts/samples/lobecorp.photon-60-static-repeat.jsonl",
                "artifacts/jfr/lobecorp.photon-60-static-repeat.jfr",
                "artifacts/custom/photon-60-static-repeat-workload.json",
                "artifacts/samples/lobecorp.photon-70-empty-repeat.jsonl",
                "artifacts/jfr/lobecorp.photon-70-empty-repeat.jfr",
                "artifacts/custom/photon-70-empty-repeat-workload.json",
                "artifacts/samples/lobecorp.photon-80-static-thirtytwo.jsonl",
                "artifacts/jfr/lobecorp.photon-80-static-thirtytwo.jfr",
                "artifacts/custom/photon-80-static-thirtytwo-workload.json",
                "artifacts/custom/photon-80-static-thirtytwo-frames.json",
                "artifacts/samples/lobecorp.photon-90-animated-thirtytwo.jsonl",
                "artifacts/jfr/lobecorp.photon-90-animated-thirtytwo.jfr",
                "artifacts/custom/photon-90-animated-thirtytwo-workload.json",
                "artifacts/custom/photon-90-animated-thirtytwo-frames.json",
                "artifacts/samples/lobecorp.photon-91-mixed-thirtytwo-laser.jsonl",
                "artifacts/jfr/lobecorp.photon-91-mixed-thirtytwo-laser.jfr",
                "artifacts/custom/photon-91-mixed-thirtytwo-laser-workload.json",
                "artifacts/custom/photon-91-mixed-thirtytwo-laser-frames.json",
                "artifacts/custom/photon-91-mixed-thirtytwo-laser-beam.png",
                "artifacts/samples/lobecorp.photon-92-mixed-sixtyfour-four-lasers.jsonl",
                "artifacts/jfr/lobecorp.photon-92-mixed-sixtyfour-four-lasers.jfr",
                "artifacts/custom/photon-92-mixed-sixtyfour-four-lasers-workload.json",
                "artifacts/custom/photon-92-mixed-sixtyfour-four-lasers-frames.json",
                "artifacts/custom/photon-92-mixed-sixtyfour-four-lasers-beam.png",
                "artifacts/samples/lobecorp.photon-99-empty-extreme-repeat.jsonl",
                "artifacts/jfr/lobecorp.photon-99-empty-extreme-repeat.jfr",
                "artifacts/custom/photon-99-empty-extreme-repeat-workload.json",
                "artifacts/custom/photon-99-empty-extreme-repeat-frames.json"
            )
        )
    } else {
        expectedScenarioIds.set(listOf("lobecorp.sweeper-render"))
        expectedMetricNames.set(listOf("client.frame.interval", "lobecorp.sweeper_render.loaded_entities"))
        expectedLoadedModIds.set(listOf("minecraft", "neoforge", modId, "modbench_runtime"))
        expectedArtifactPaths.set(
            listOf(
                "artifacts/samples/lobecorp.sweeper-render.jsonl",
                "artifacts/jfr/lobecorp.sweeper-render.jfr",
                "artifacts/screenshots/sweeper-render.png"
            )
        )
    }
}

if (photonBenchEnabled.get()) {
    val photonShader = layout.projectDirectory.file("run/client/shaderpacks/ComplementaryReimagined_r5.9.3.zip")
    val photonShaderEnabled = providers.gradleProperty("photonShader").map(String::toBoolean).orElse(true)
    val photonSparkBackground = providers.gradleProperty("photonSpark").map(String::toBoolean).orElse(true)
    val photonBenchDirectory = layout.buildDirectory.dir("modBench/runs/default/client")
    tasks.named("prepareBenchClientOptions") {
        inputs.file(photonShader)
        inputs.property("photonShaderEnabled", photonShaderEnabled)
        inputs.property("photonSparkBackground", photonSparkBackground)
        doLast {
            val directory = photonBenchDirectory.get().asFile
            val shaderDirectory = directory.resolve("shaderpacks")
            shaderDirectory.mkdirs()
            photonShader.asFile.copyTo(shaderDirectory.resolve(photonShader.asFile.name), overwrite = true)
            directory.resolve("config").mkdirs()
            val shaderEnabled = photonShaderEnabled.get()
            directory.resolve("config/iris.properties").writeText(
                "enableShaders=" + shaderEnabled + "\nshaderPack=" +
                        (if (shaderEnabled) photonShader.asFile.name else "") + "\n"
            )
            val sparkDirectory = directory.resolve("config/spark")
            sparkDirectory.mkdirs()
            val configuration = sparkDirectory.resolve("config.json")
            val text = if (configuration.exists()) configuration.readText() else "{}"
            val values = groovy.json.JsonSlurper().parseText(text) as Map<*, *>
            val updated = values.entries.associate { it.key.toString() to it.value }.toMutableMap()
            updated["backgroundProfiler"] = photonSparkBackground.get()
            configuration.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(updated)))
        }
    }
}

modBench {
    targetMod = modId
    // 测试矩阵按需只跑指定场景，避免每次重启都带跑其它场景。
    scenarioFilter.set(providers.gradleProperty("benchScenarios").orElse(""))
    // 2分钟浪潮需要2400测量tick，放大阶段超时以免被默认1200截断。
    phaseTimeoutTicks.set(3600L)
    expectedProviderCount = 1
    seed = 7
    jfrEnabled = true
    serverLevelType = "minecraft:flat"
    clientWorldPreset = "flat"
    clientWorldId = "lobecorp-bench"
    clientWindowWidth = providers.gradleProperty("benchWindowWidth").map(String::toInt).getOrElse(2560)
    clientWindowHeight = providers.gradleProperty("benchWindowHeight").map(String::toInt).getOrElse(1494)
}

tasks.named<VerifyBenchReportTask>("verifyBenchServerReport") {
    expectedScenarioIds.set(listOf("lobecorp.sweeper-ai"))
    expectedMetricNames.set(listOf("server.tick.duration", "lobecorp.sweeper_ai.loaded_entities"))
    expectedLoadedModIds.set(listOf("minecraft", "neoforge", modId, "modbench_runtime"))
    expectedArtifactPaths.set(
        listOf(
            "artifacts/samples/lobecorp.sweeper-ai.jsonl",
            "artifacts/jfr/lobecorp.sweeper-ai.jfr"
        )
    )
}

tasks.named<VerifyBenchReportTask>("verifyBenchClientReport") {
    expectedScenarioIds.set(listOf("lobecorp.sweeper-render", "lobecorp.horde-render"))
    expectedMetricNames.set(listOf("client.frame.interval", "lobecorp.sweeper_render.loaded_entities", "lobecorp.horde_render.instances"))
    expectedLoadedModIds.set(listOf("minecraft", "neoforge", modId, "modbench_runtime"))
    expectedArtifactPaths.set(
        listOf(
            "artifacts/samples/lobecorp.sweeper-render.jsonl",
            "artifacts/jfr/lobecorp.sweeper-render.jfr",
            "artifacts/screenshots/sweeper-render.png",
            "artifacts/samples/lobecorp.horde-render.jsonl",
            "artifacts/jfr/lobecorp.horde-render.jfr",
            "artifacts/screenshots/horde-render.png"
        )
    )
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

val minecraftMcpPort = minecraftMcpGamePort

tasks.register<Exec>("checkMinecraftMcpBridge") {
    group = "minecraft mcp"
    workingDir(minecraftMcpBridge)
    commandLine("node", "dist/cli.js", "--help")
}
listOf(
    "probeBridge" to emptyList(),
    "probeEffectRuntime" to listOf("--runtime"),
    "probeEffectAuthoring" to listOf("--authoring"),
    "probeEffectAnimation" to listOf("--animation"),
    "inspectReferenceEffects" to listOf("inspect"),
    "createReferenceEffects" to listOf("apply"),
    "saveReferenceEffects" to listOf("save"),
).forEach { (taskName, arguments) ->
    tasks.register<Exec>(taskName) {
        group = "minecraft mcp"
        workingDir(minecraftMcpBridge)
        val script =
            if (taskName.endsWith("ReferenceEffects")) "create-reference-effects.mjs" else "check-effect-tools.mjs"
        commandLine(listOf("node", "scripts/$script") + arguments)
        environment("MC_MCP_PORT", minecraftMcpPort.get())
    }
}

plugins.withType<IdeaExtPlugin> {

val projectSettings = (idea.project as ExtensionAware).extensions.getByType<ProjectSettings>()
    val runConfigurations = (projectSettings as ExtensionAware).extensions.getByType<RunConfigurationContainer>()
    runConfigurations.create("Workspace Build", Gradle::class.java).apply {
        projectPath = rootDir.absolutePath
        taskNames = listOf("build")
    }

    listOf(
        "BenchMod Compile" to listOf("compileBenchJava", "verifyProductionJarHasNoBenchContent"),
        "BenchMod Server" to listOf("verifyBenchServer"),
        "BenchMod Client" to listOf("verifyBenchClient"),
    ).forEach { (name, benchTasks) ->
        runConfigurations.create(name, Gradle::class.java).apply {
            projectPath = rootDir.absolutePath
            taskNames = benchTasks
        }
    }
    runConfigurations.create("Minecraft MCP Artifact Check", Gradle::class.java).apply {
        projectPath = rootDir.absolutePath
        taskNames = listOf("compileJava", "prepareClientRun", "checkMinecraftMcpBridge", "probeBridge")
    }
    runConfigurations.create("Photon Bench Debug", Application::class.java).apply {
        mainClass = "net.neoforged.devlaunch.Main"
        moduleRef(project, photonBenchClient)
        workingDirectory =
            layout.buildDirectory.dir("modBench/runs/default/client").get().asFile.invariantSeparatorsPath
        programParameters = "@" + layout.buildDirectory.file("moddev/benchClientRunProgramArgs.txt")
            .get().asFile.invariantSeparatorsPath
        val folders = (sourceSets.main.get().output.files + sourceSets["bench"].output.files)
            .joinToString(";") { "$modId%%${it.invariantSeparatorsPath}" }
        jvmArgs =
            "@" + layout.buildDirectory.file("moddev/benchClientRunVmArgs.txt").get().asFile.invariantSeparatorsPath +
                    " -Dfml.modFolders=$folders"
    }
    runConfigurations.create("BenchMod Photon Prepare", Gradle::class.java).apply {
        projectPath = rootDir.absolutePath
        taskNames = listOf("benchClasses", "prepareBenchClientRun", "prepareBenchClientOptions")
        scriptParameters = "-PphotonBench=true"
    }
    runConfigurations.create("BenchMod Photon", Gradle::class.java).apply {
        projectPath = rootDir.absolutePath
        taskNames = listOf("verifyBenchClient")
        scriptParameters = "-PphotonBench=true"
    }
    runConfigurations.create("Minecraft MCP Bridge", Gradle::class.java).apply {
        projectPath = rootDir.absolutePath
        taskNames = listOf("checkMinecraftMcpBridge", "probeBridge")
    }
    runConfigurations.create("Minecraft MCP Effect Runtime", Gradle::class.java).apply {
        projectPath = rootDir.absolutePath
        taskNames = listOf("probeEffectRuntime")
    }
    runConfigurations.create("Minecraft MCP Effect Authoring", Gradle::class.java).apply {
        projectPath = rootDir.absolutePath
        taskNames = listOf("probeEffectAuthoring")
    }
    listOf(
        "Minecraft MCP Reference Inspect" to "inspectReferenceEffects",
        "Minecraft MCP Reference Create" to "createReferenceEffects",
        "Minecraft MCP Reference Save" to "saveReferenceEffects",
    ).forEach { (name, task) ->
        runConfigurations.create(name, Gradle::class.java).apply {
            projectPath = rootDir.absolutePath
            taskNames = listOf(task)
        }
    }
}

idea.module {
    isDownloadSources = true
    isDownloadJavadoc = true
}
