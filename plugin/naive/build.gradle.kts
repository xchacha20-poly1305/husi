plugins {
    id("com.android.application")
}

extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
    defaultConfig {
        applicationId = "fr.husi.plugin.naive"
    }
    namespace = "fr.husi.plugin.naive"
}

setupPlugin("naive")
