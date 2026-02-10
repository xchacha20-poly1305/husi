plugins {
    id("com.android.library")
    id("kotlin-parcelize")
}

setupKotlinCommon()
extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    namespace = "fr.husi.plugin"
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
