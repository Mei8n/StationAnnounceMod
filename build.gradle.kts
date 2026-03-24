plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

version = property("modVersion").toString()
group = property("modGroup").toString()

minecraft {
}

dependencies {
    // libsフォルダ内の全てのjarをコンパイル時のみ参照する
    compileOnly fileTree(dir: 'libs', include: ['*.jar'])
}
