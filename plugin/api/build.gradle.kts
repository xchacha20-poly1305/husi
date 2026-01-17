plugins {
    id("com.android.library")
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
