plugins {
    id("com.android.application")
}

android {
    defaultConfig {
        applicationId = "fr.husi.plugin.juicity"
    }
    namespace = "fr.husi.plugin.juicity"
}

setupPlugin("juicity")