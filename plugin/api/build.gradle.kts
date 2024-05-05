plugins {
    id("com.android.library")
    kotlin("android")
    id("kotlin-parcelize")
}

setupKotlinCommon()
android {
    namespace = "io.nekohasekai.sagernet.plugin"
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
