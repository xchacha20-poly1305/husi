package fr.husi.platform

data class PathLimits(
    val maxNameLength: Int,
    val maxPathLength: Int,
    val countsUtf8Bytes: Boolean,
) {

    fun lengthOf(text: String): Int = if (countsUtf8Bytes) {
        text.encodeToByteArray().size
    } else {
        text.length
    }

    fun acceptsName(name: String): Boolean = lengthOf(name) <= maxNameLength

    fun acceptsPath(path: String): Boolean = lengthOf(path) <= maxPathLength

    companion object {

        /** ext4 and f2fs allow 255 bytes per component, and `PATH_MAX` is 4096 bytes. */
        val Linux = PathLimits(
            maxNameLength = 255,
            maxPathLength = 4096,
            countsUtf8Bytes = true,
        )

        /** APFS allows 255 UTF-8 bytes per component, and `PATH_MAX` is 1024 bytes. */
        val MacOs = PathLimits(
            maxNameLength = 255,
            maxPathLength = 1024,
            countsUtf8Bytes = true,
        )

        /**
         * NTFS allows 255 UTF-16 code units per component. `MAX_PATH` is 260 including the
         * terminating NUL, which long paths lift only when both the system and the manifest opt
         * in, so stay on the number every Windows accepts.
         */
        val Windows = PathLimits(
            maxNameLength = 255,
            maxPathLength = 259,
            countsUtf8Bytes = false,
        )

        val current: PathLimits
            get() = when (PlatformInfo.platform) {
                Platform.Android, Platform.Linux -> Linux
                Platform.MacOs -> MacOs
                Platform.Windows -> Windows
            }
    }
}
