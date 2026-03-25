plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

version = property("modVersion").toString()
group = property("modGroup").toString()

minecraft {
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}
