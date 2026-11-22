import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

/**
 * Merges the keep rules that libraries ship in their jars under `META-INF/proguard/`.
 *
 * R8 applies these rules on its own; ProGuard, which Compose desktop runs for release builds,
 * never reads them. A library whose native code calls back into Kotlin by name then loses those
 * callbacks in the shrinker, and the failure only shows at run time.
 */
abstract class CollectLibraryProguardRulesTask : DefaultTask() {
    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun collect() {
        val rules = buildString {
            val jars = runtimeClasspath.files
                .filter { it.isFile && it.extension == "jar" }
                .sortedBy { it.name }
            for (jar in jars) {
                ZipFile(jar).use { zip ->
                    val ruleEntries = zip.entries().asSequence()
                        .filter { !it.isDirectory && it.name.startsWith(LIBRARY_RULES_DIRECTORY) }
                        .sortedBy { it.name }
                    for (entry in ruleEntries) {
                        appendLine("# ${jar.name}!/${entry.name}")
                        appendLine(zip.getInputStream(entry).bufferedReader().use { it.readText() })
                    }
                }
            }
        }
        outputFile.get().asFile.also {
            it.parentFile.mkdirs()
            it.writeText(rules)
        }
    }

    private companion object {
        const val LIBRARY_RULES_DIRECTORY = "META-INF/proguard/"
    }
}
