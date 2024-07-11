plugins {
    id("com.android.application")
}

android {
    defaultConfig {
        applicationId = "fr.husi.plugin.naive"
    }
    namespace = "fr.husi.plugin.naive"
}

setupPlugin("naive")
