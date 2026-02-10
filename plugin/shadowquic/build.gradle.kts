plugins {
    id("com.android.application")
}

extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
    defaultConfig {
        applicationId = "fr.husi.plugin.shadowquic"
    }
    namespace = "fr.husi.plugin.shadowquic"
}

setupPlugin("shadowquic")
