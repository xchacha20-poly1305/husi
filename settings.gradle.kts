include(":plugin:api")

val buildPlugin = System.getenv("BUILD_PLUGIN")
when {
    buildPlugin.isNullOrBlank() -> {
        include(":plugin:hysteria2")
        include(":plugin:juicity")
        include(":plugin:naive")
        include(":plugin:mieru")
        include(":plugin:shadowquic")
    }
    buildPlugin == "none" -> {
    }
    else -> {
        include(":plugin:$buildPlugin")
    }
}

include(":app")
include(":library:DragDropSwipeLazyColumn:drag-drop-swipe-lazycolumn")

rootProject.name = "husi"
