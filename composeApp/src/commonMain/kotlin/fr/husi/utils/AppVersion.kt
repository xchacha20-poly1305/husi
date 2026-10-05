package fr.husi.utils

enum class PreReleaseChannel(val label: String) {
    ALPHA("alpha"),
    BETA("beta"),
    RC("rc"),
}

/** [number] is null for a bare label such as `rc`, which ranks below every numbered one. */
data class PreRelease(
    val channel: PreReleaseChannel,
    val number: Int? = null,
) : Comparable<PreRelease> {
    override fun compareTo(other: PreRelease): Int = order.compare(this, other)

    private companion object {
        val order = compareBy(PreRelease::channel).thenBy(nullsFirst(), PreRelease::number)
    }
}

data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: PreRelease? = null,
) : Comparable<AppVersion> {

    val isPreRelease: Boolean
        get() = preRelease != null

    override fun compareTo(other: AppVersion): Int = order.compare(this, other)

    companion object {
        private const val PRE_RELEASE_SEPARATOR = '-'

        // nullsLast: a final release ranks above every pre-release of the same version.
        private val order = compareBy(AppVersion::major, AppVersion::minor, AppVersion::patch)
            .thenBy(nullsLast(), AppVersion::preRelease)

        fun parse(text: String): AppVersion? {
            val version = text.trim()
                .removePrefix("v")
                .substringBefore("+")
            val core = version.substringBefore(PRE_RELEASE_SEPARATOR)
                .split(".")
                .takeIf { it.size == 3 }
                ?: return null
            val preRelease = if (PRE_RELEASE_SEPARATOR in version) {
                parsePreRelease(version.substringAfter(PRE_RELEASE_SEPARATOR)) ?: return null
            } else {
                null
            }
            return AppVersion(
                major = core[0].toVersionNumber() ?: return null,
                minor = core[1].toVersionNumber() ?: return null,
                patch = core[2].toVersionNumber() ?: return null,
                preRelease = preRelease,
            )
        }

        private fun parsePreRelease(text: String): PreRelease? {
            val channel = PreReleaseChannel.entries.find { text.startsWith(it.label) }
                ?: return null
            val numberText = text.removePrefix(channel.label)
            if (numberText.isEmpty()) return PreRelease(channel)
            return PreRelease(
                channel = channel,
                number = numberText.removePrefix(".")
                    .toVersionNumber()
                    ?: return null,
            )
        }

        private fun String.toVersionNumber(): Int? {
            if (isEmpty() || any { it !in '0'..'9' }) return null
            return toIntOrNull()
        }
    }
}
