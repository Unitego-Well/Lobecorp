plugins {
    `java-library`
    alias(libs.plugins.moddev)
}

group = "xyz.langyo"
version = "0.3.0"

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

repositories {
    mavenCentral()
}

sourceSets.main {
    java.srcDirs("packages/common/src/main/java", "packages/mods/26.1.2/neoforge/src/main/java")
    resources.srcDirs("packages/common/src/main/resources", "packages/mods/26.1.2/neoforge/src/main/resources")
}

dependencies {
    implementation("org.java-websocket:Java-WebSocket:1.5.4")
}

neoForge {
    version = libs.versions.neoforge.get()
    mods {
        register("mcpmod") {
            sourceSet(sourceSets.main.get())
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

val bridgeDirectory = layout.projectDirectory.dir("packages/minecraft-mod-mcp")
val npmCommand = if (System.getProperty("os.name").startsWith("Windows")) {
    listOf("cmd", "/d", "/c", "npm")
} else {
    listOf("npm")
}

val installBridge = tasks.register<Exec>("installBridge") {
    group = "minecraft mcp"
    description = "Installs the pinned Minecraft MCP bridge dependencies"
    workingDir(bridgeDirectory)
    commandLine(npmCommand + listOf("install", "--ignore-scripts", "--no-audit", "--no-fund"))
    inputs.file(bridgeDirectory.file("package.json"))
    outputs.dir(bridgeDirectory.dir("node_modules"))
    outputs.file(bridgeDirectory.file("package-lock.json"))
}

tasks.register<Exec>("buildBridge") {
    group = "minecraft mcp"
    description = "Builds the project-local Minecraft MCP stdio bridge"
    dependsOn(installBridge)
    workingDir(bridgeDirectory)
    commandLine(npmCommand + listOf("run", "build"))
    inputs.dir(bridgeDirectory.dir("src"))
    inputs.file(bridgeDirectory.file("tsup.config.ts"))
    outputs.dir(bridgeDirectory.dir("dist"))
}

tasks.register<Exec>("checkBridge") {
    group = "minecraft mcp"
    description = "Checks the project-local Minecraft MCP TypeScript sources"
    dependsOn(installBridge)
    workingDir(bridgeDirectory)
    commandLine(npmCommand + listOf("run", "lint"))
}

tasks.register<Exec>("probeBridge") {
    group = "minecraft mcp"
    description = "Checks the compiled MCP bridge tool discovery through stdio"
    dependsOn("buildBridge")
    workingDir(bridgeDirectory)
    commandLine(npmCommand + listOf("run", "check:tools"))
}

tasks.register<Exec>("probeEffectRuntime") {
    group = "minecraft mcp"
    description = "Reads the effect capabilities and state from a running Minecraft client"
    dependsOn("buildBridge")
    workingDir(bridgeDirectory)
    commandLine(npmCommand + listOf("run", "check:runtime"))
}

tasks.register<Exec>("probeEffectAuthoring") {
    group = "minecraft mcp"
    description = "Checks authoring in a new model project and keeps its example files"
    dependsOn("buildBridge")
    workingDir(bridgeDirectory)
    commandLine(npmCommand + listOf("run", "check:authoring"))
}

listOf(
    "inspectReferenceEffects" to "inspect",
    "createReferenceEffects" to "apply",
    "saveReferenceEffects" to "save"
).forEach { (taskName, phase) ->
    tasks.register<Exec>(taskName) {
        group = "minecraft mcp"
        description = "Runs the reference effect authoring phase: $phase"
        dependsOn("buildBridge")
        workingDir(bridgeDirectory)
        commandLine(npmCommand + listOf("run", "reference:effects", "--", phase))
    }
}

tasks.register<Copy>("importIcons") {
    group = "minecraft mcp"
    description = "Imports the original MCP icon assets without replacing existing files"
    val iconNames = listOf("resume.png", "transfer.png")
    val sourceDirectory = rootProject.layout.projectDirectory
        .dir("run/client/.mcp-repair-build/packages/common/src/main/resources/assets/mcpmod/icons")
    val targetDirectory = layout.projectDirectory
        .dir("packages/common/src/main/resources/assets/mcpmod/icons")
    from(sourceDirectory) {
        include(iconNames)
    }
    into(targetDirectory)
    doFirst {
        check(iconNames.all { sourceDirectory.file(it).asFile.isFile }) {
            "MCP icon sources are missing from the original repair workspace."
        }
        check(iconNames.none { targetDirectory.file(it).asFile.exists() }) {
            "MCP icon imports do not overwrite existing assets."
        }
    }
}
