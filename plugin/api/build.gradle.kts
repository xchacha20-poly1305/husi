plugins {
    id("com.android.library")
    id("kotlin-parcelize")
}

setupKotlinCommon()
android {
    namespace = "fr.husi.plugin"
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
