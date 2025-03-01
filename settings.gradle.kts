include(":plugin:api")

val buildPlugin = System.getenv("BUILD_PLUGIN")
if (!buildPlugin.isNullOrBlank()) {
    include(":plugin:$buildPlugin")
}

include(":app")

rootProject.name = "husi"
