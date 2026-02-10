plugins {
    id("com.android.application")
}

extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
    defaultConfig {
        applicationId = "fr.husi.plugin.mieru"
    }
    namespace = "fr.husi.plugin.mieru"
}

setupPlugin("mieru")
