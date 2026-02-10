plugins {
    id("com.android.application")
}

extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
    defaultConfig {
        applicationId = "fr.husi.plugin.hysteria2"
    }
    namespace = "fr.husi.plugin.hysteria2"
}

setupPlugin("hysteria2")
