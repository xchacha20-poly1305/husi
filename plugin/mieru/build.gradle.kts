plugins {
    id("com.android.application")
}

android {
    defaultConfig {
        applicationId = "fr.husi.plugin.mieru"
    }
    namespace = "fr.husi.plugin.mieru"
}

setupPlugin("mieru")