import java.util.regex.Pattern

plugins {
    id("com.gtnewhorizons.retrofuturagradle")
}

// === Javaソースから情報を抽出 ===
val modCoreFile = file("src/main/java/jp/me1han/sam/StationAnnounceModCore.java")

fun findInSource(name: String): String {
    val content = modCoreFile.readText()
    val pattern = Pattern.compile("public\\s+static\\s+final\\s+String\\s+$name\\s*=\\s*\"([^\"]*)\"\\s*;")
    val matcher = pattern.matcher(content)
    return if (matcher.find()) matcher.group(1) else "unknown"
}

val modId = findInSource("MOD_ID")
val modName = findInSource("MOD_NAME")
val baseVersion = findInSource("VERSION")
val minecraftVersion = "1.7.10"

// === ビルドフラグ判定 ===
val isStandalone = project.hasProperty("noLibs") ||
    gradle.startParameter.taskNames.any { it.contains("NoLibs", ignoreCase = true) || it.contains("Standalone", ignoreCase = true) }
val isRelease = project.hasProperty("release") ||
    gradle.startParameter.taskNames.any { it.contains("buildRelease", ignoreCase = true) }

val modVersion = baseVersion + (if (isRelease) "" else "-SNAPSHOT")
val fileVersion = if (isRelease) baseVersion else "SNAPSHOT"

group = "jp.me1han.sam"
version = modVersion

// Java 8 徹底
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

minecraft {
    mcVersion.set(minecraftVersion)
}

// === JARファイル設定 ===
tasks.withType<Jar> {
    archiveBaseName.set(modId)
    archiveVersion.set(fileVersion)

    manifest {
        attributes(
            "FMLCorePluginContainsFMLMod" to "true",
            "ForceLoadAsMod" to "true"
        )
    }
}

tasks.jar {
    archiveClassifier.set("")
}

val devJar = tasks.register<Jar>("devJar") {
    archiveClassifier.set("dev")
    from(sourceSets.main.get().output)
}

// === タスク依存関係 ===
tasks.named("reobfJar") {
    mustRunAfter(devJar)
}

tasks.named("build") {
    dependsOn(devJar)
}

// === リソース処理（ここをより安全に修正） ===
tasks.withType<ProcessResources> {
    // 文字化けや意図しない置換を防ぐ
    filteringCharset = "UTF-8"
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    val properties = mapOf(
        "modId" to modId,
        "modName" to modName,
        "modVersion" to modVersion,
        "minecraftVersion" to minecraftVersion
    )
    inputs.properties(properties)

    filesMatching("mcmod.info") {
        expand(properties)
    }
}

dependencies {
    if (!isStandalone) {
        implementation(fileTree("libs") { include("*.jar") })
    }
}

repositories {
    mavenCentral()
    mavenLocal()
}

// --- カスタムタスク ---
tasks.register("buildRelease") { group = "build"; dependsOn("build") }
tasks.register("buildStandalone") { group = "build"; dependsOn("build") }
tasks.register("runClientNoLibs") { group = "forgegradleruns"; dependsOn("runClient") }

tasks.register("processIdeaSettings") {
}
