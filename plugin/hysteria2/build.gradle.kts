plugins {
    id("com.android.application")
}

android {
    defaultConfig {
        applicationId = "fr.husi.plugin.hysteria2"
    }
    namespace = "fr.husi.plugin.hysteria2"
}

setupPlugin("hysteria2")