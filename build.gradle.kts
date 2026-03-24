plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

base {
    archivesName.set("StationAnnounceMod")
}

version = property("modVersion").toString()
group = property("modGroup").toString()

minecraft {
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}
