plugins {
    id("com.android.application")
}

extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
    defaultConfig {
        applicationId = "fr.husi.plugin.juicity"
    }
    namespace = "fr.husi.plugin.juicity"
}

setupPlugin("juicity")
