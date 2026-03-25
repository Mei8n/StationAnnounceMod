import java.util.regex.Pattern

plugins {
    id("com.gtnewhorizons.retrofuturagradle")
}

// === Javaソース（StationAnnounceModCore.java）から情報を抽出 ===
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

// === ビルドフラグの判定（構成キャッシュ対応） ===
val isStandalone = project.hasProperty("noLibs") ||
    gradle.startParameter.taskNames.any { it.contains("NoLibs", ignoreCase = true) || it.contains("Standalone", ignoreCase = true) }

val isRelease = project.hasProperty("release") ||
    gradle.startParameter.taskNames.any { it.contains("buildRelease", ignoreCase = true) }

// === バージョン・ファイル名の規則（ここを徹底） ===
// 1. 内部バージョン (mcmod.info等用)
val modVersion = baseVersion + (if (isRelease) "" else "-SNAPSHOT")

// 2. 出力ファイル名用のバージョン (SNAPSHOT時は、スタンドアロンに関わらず "SNAPSHOT" に固定)
val fileVersion = if (isRelease) baseVersion else "SNAPSHOT"

group = "jp.me1han.sam"
version = modVersion

minecraft {
    mcVersion.set(minecraftVersion)
}

// === JARファイル生成の設定 ===

// 全てのJarタスク（jar, devJar）に共通のベース名とバージョンを強制適用
tasks.withType<Jar> {
    archiveBaseName.set(modId)
    archiveVersion.set(fileVersion) // ここから standaloneSuffix を削除し、SNAPSHOTに徹底

    manifest {
        attributes(
            "FMLCorePluginContainsFMLMod" to "true",
            "ForceLoadAsMod" to "true"
        )
    }
}

// A. 配布用Jar (難読化されるメインファイル)
tasks.jar {
    archiveClassifier.set("")
}

// B. 開発用Jar (難読化されない -dev ファイル)
val devJar = tasks.register<Jar>("devJar") {
    archiveClassifier.set("dev")
    from(sourceSets.main.get().output)
}

// === タスクの依存関係と順序の整理（エラー防止） ===

tasks.named("reobfJar") {
    // 難読化対象を通常のjarタスクに固定し、devJarとの競合を避ける
    mustRunAfter(devJar)
}

tasks.named("build") {
    // build実行時に、通常版とdev版の両方を必ず生成する
    dependsOn(devJar)
}

// --- リソース（mcmod.info）の処理 ---
tasks.withType<ProcessResources> {
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

// --- 依存関係の制御 (スタンドアロン判定はここでのみ使用) ---
dependencies {
    if (!isStandalone) {
        // 通常ビルド：libsフォルダ内のModを読み込む
        implementation(fileTree("libs") { include("*.jar") })
        logger.lifecycle("SAM Build [Standard]: $modName v$version")
    } else {
        // スタンドアロン：ビルドパスからlibsを除外するが、ファイル名は変えない
        logger.lifecycle("SAM Build [Standalone]: $modName v$version")
    }
}

repositories {
    mavenCentral()
    mavenLocal()
}

// --- 実行構成用カスタムタスク ---
tasks.register("buildRelease") { group = "build"; dependsOn("build") }
tasks.register("buildStandalone") { group = "build"; dependsOn("build") }
tasks.register("runClientNoLibs") { group = "forgegradleruns"; dependsOn("runClient") }
