plugins {
    id("com.android.application")
}

android {
    defaultConfig {
        applicationId = "fr.husi.plugin.shadowquic"
    }
    namespace = "fr.husi.plugin.shadowquic"
}

setupPlugin("shadowquic")