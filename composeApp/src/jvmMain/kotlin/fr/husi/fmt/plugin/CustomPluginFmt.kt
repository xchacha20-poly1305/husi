package fr.husi.fmt.plugin

import fr.husi.PLUGIN_CA_FILE
import fr.husi.fmt.LOCALHOST4
import fr.husi.ktx.invariantPathString
import fr.husi.ktx.joinAddress
import fr.husi.ktx.readText
import fr.husi.logLevelString
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.resolve
import kotlinx.serialization.json.JsonPrimitive
import fr.husi.fmt.protectPath as defaultProtectPath

const val PLACEHOLDER_LISTEN_HOST = "{{listen_host}}"
const val PLACEHOLDER_LISTEN_PORT = "{{listen_port}}"
const val PLACEHOLDER_LISTEN_ADDR = "{{listen_addr}}"
const val PLACEHOLDER_FINAL_HOST = "{{final_host}}"
const val PLACEHOLDER_FINAL_PORT = "{{final_port}}"
const val PLACEHOLDER_FINAL_ADDR = "{{final_addr}}"
const val PLACEHOLDER_SERVER_HOST = "{{server_host}}"
const val PLACEHOLDER_SERVER_PORT = "{{server_port}}"
const val PLACEHOLDER_CONFIG = "{{config}}"
const val PLACEHOLDER_PROTECT_PATH = "{{protect_path}}"
const val PLACEHOLDER_CA_PATH = "{{ca_path}}"
const val PLACEHOLDER_CA_CONTENT = "{{ca_content}}"
const val PLACEHOLDER_CA_CONTENT_JSON = "{{ca_content_json}}"
const val PLACEHOLDER_LOG_LEVEL = "{{log_level}}"
const val PLACEHOLDER_CACHE_DIR = "{{cache_dir}}"

data class CustomPluginRenderContext(
    val port: Int,
    val shouldProtect: Boolean = false,
    val logLevel: Int = 3,
    val protectPath: String = "",
    val caPath: String = "",
    val caContent: String = "",
    val cacheDir: String = "",
    val configFileToken: String = "",
)

data class PluginCaInfo(
    val path: String,
    val content: String,
)

fun readPluginCaInfo(externalAssetsDir: PlatformFile): PluginCaInfo {
    val file = externalAssetsDir.resolve(PLUGIN_CA_FILE).takeIf { it.isRegularFile() }
        ?: return PluginCaInfo(path = "", content = "")
    return PluginCaInfo(
        path = file.invariantPathString(),
        content = file.readText(),
    )
}

fun CustomPluginBean.sanitizedConfigFileName(port: Int): String {
    val cleaned = buildString(configFileName.length) {
        for (char in configFileName.trim()) {
            if (char != '/' && char != '\\' && char != '\u0000') {
                append(char)
            }
        }
    }
    if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") {
        return "custom_${port}.json"
    }
    return "custom_${port}_$cleaned"
}

private enum class ArgTokenizerState {
    NORMAL,
    SINGLE_QUOTED,
    DOUBLE_QUOTED,
}

fun parseCommandArgs(raw: String): List<String> {
    if (raw.isEmpty()) return emptyList()
    val result = ArrayList<String>()
    val current = StringBuilder()
    var state = ArgTokenizerState.NORMAL
    var hasToken = false
    var i = 0
    while (i < raw.length) {
        val char = raw[i]
        when (state) {
            ArgTokenizerState.NORMAL -> when {
                char == '\'' -> {
                    state = ArgTokenizerState.SINGLE_QUOTED
                    hasToken = true
                }

                char == '"' -> {
                    state = ArgTokenizerState.DOUBLE_QUOTED
                    hasToken = true
                }

                char == '\\' -> {
                    if (i + 1 < raw.length) {
                        i++
                        current.append(raw[i])
                    } else {
                        current.append('\\')
                    }
                    hasToken = true
                }

                char.isWhitespace() -> {
                    if (hasToken) {
                        result.add(current.toString())
                        current.setLength(0)
                        hasToken = false
                    }
                }

                else -> {
                    current.append(char)
                    hasToken = true
                }
            }

            ArgTokenizerState.SINGLE_QUOTED -> {
                if (char == '\'') {
                    state = ArgTokenizerState.NORMAL
                } else {
                    current.append(char)
                }
            }

            ArgTokenizerState.DOUBLE_QUOTED -> when (char) {
                '"' -> state = ArgTokenizerState.NORMAL
                '\\' -> {
                    if (i + 1 < raw.length && (raw[i + 1] == '"' || raw[i + 1] == '\\')) {
                        i++
                        current.append(raw[i])
                    } else {
                        current.append('\\')
                    }
                }

                else -> current.append(char)
            }
        }
        i++
    }
    if (hasToken) {
        result.add(current.toString())
    }
    return result
}

fun parseEnvironment(raw: String): Map<String, String> {
    if (raw.isBlank()) return emptyMap()
    val result = LinkedHashMap<String, String>()
    for (line in raw.lineSequence()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith('#')) {
            continue
        }
        val equalIndex = trimmed.indexOf('=')
        if (equalIndex <= 0) continue
        val key = trimmed.substring(0, equalIndex).trim()
        if (key.isEmpty()) continue
        val value = trimmed.substring(equalIndex + 1)
        result[key] = value
    }
    return result
}

fun expandPlaceholders(template: String, values: Map<String, String>): String {
    if (!template.contains("{{") || values.isEmpty()) {
        return template
    }
    val result = StringBuilder(template.length)
    var cursor = 0
    while (cursor < template.length) {
        val start = template.indexOf("{{", cursor)
        if (start < 0) {
            result.append(template, cursor, template.length)
            break
        }
        result.append(template, cursor, start)
        val end = template.indexOf("}}", start + 2)
        if (end < 0) {
            result.append(template, start, template.length)
            break
        }
        val nestedStart = template.indexOf("{{", start + 2)
        if (nestedStart in 0 until end) {
            result.append(template, start, nestedStart)
            cursor = nestedStart
            continue
        }
        val token = template.substring(start, end + 2)
        val replacement = values[token]
        if (replacement != null) {
            result.append(replacement)
        } else {
            result.append(token)
        }
        cursor = end + 2
    }
    return result.toString()
}

fun escapeJsonStringContent(raw: String): String {
    return JsonPrimitive(raw).toString().removeSurrounding("\"")
}

private fun CustomPluginBean.buildPlaceholderValues(
    context: CustomPluginRenderContext,
    includeConfig: Boolean,
): Map<String, String> {
    val finalPortStr = if (finalPort > 0) {
        finalPort.toString()
    } else {
        ""
    }
    val finalAddrStr = if (finalAddress.isNotBlank() && finalPort > 0) {
        joinAddress(finalAddress, finalPort)
    } else {
        ""
    }
    val serverPortStr = if (serverPort > 0) {
        serverPort.toString()
    } else {
        ""
    }
    return buildMap(15) {
        put(PLACEHOLDER_LISTEN_HOST, LOCALHOST4)
        put(PLACEHOLDER_LISTEN_PORT, context.port.toString())
        put(PLACEHOLDER_LISTEN_ADDR, joinAddress(LOCALHOST4, context.port))
        put(PLACEHOLDER_FINAL_HOST, finalAddress)
        put(PLACEHOLDER_FINAL_PORT, finalPortStr)
        put(PLACEHOLDER_FINAL_ADDR, finalAddrStr)
        put(PLACEHOLDER_SERVER_HOST, serverAddress)
        put(PLACEHOLDER_SERVER_PORT, serverPortStr)
        if (includeConfig) {
            put(PLACEHOLDER_CONFIG, context.configFileToken)
        }
        put(
            PLACEHOLDER_PROTECT_PATH,
            if (context.shouldProtect) {
                context.protectPath
            } else {
                ""
            },
        )
        put(PLACEHOLDER_CA_PATH, context.caPath)
        put(PLACEHOLDER_CA_CONTENT, context.caContent)
        put(PLACEHOLDER_CA_CONTENT_JSON, escapeJsonStringContent(context.caContent))
        put(PLACEHOLDER_LOG_LEVEL, logLevelString(context.logLevel))
        put(PLACEHOLDER_CACHE_DIR, context.cacheDir)
    }
}

fun CustomPluginBean.renderConfig(context: CustomPluginRenderContext): String {
    if (config.isEmpty()) return ""
    return expandPlaceholders(config, buildPlaceholderValues(context, includeConfig = false))
}

fun CustomPluginBean.renderCommandArgs(context: CustomPluginRenderContext): List<String> {
    val values = buildPlaceholderValues(context, includeConfig = true)
    return parseCommandArgs(commandArgs).map {
        expandPlaceholders(it, values)
    }
}

fun CustomPluginBean.renderEnvironment(context: CustomPluginRenderContext): Map<String, String> {
    val values = buildPlaceholderValues(context, includeConfig = true)
    return parseEnvironment(environment).mapValues { (_, value) ->
        expandPlaceholders(value, values)
    }
}

fun CustomPluginBean.buildCustomPluginConfig(
    port: Int,
    shouldProtect: Boolean,
    logLevel: Int,
    caPath: String = "",
    caContent: String = "",
    cacheDir: String = "",
): String {
    return renderConfig(
        CustomPluginRenderContext(
            port = port,
            shouldProtect = shouldProtect,
            logLevel = logLevel,
            protectPath = if (shouldProtect) {
                defaultProtectPath
            } else {
                ""
            },
            caPath = caPath,
            caContent = caContent,
            cacheDir = cacheDir,
        ),
    )
}
