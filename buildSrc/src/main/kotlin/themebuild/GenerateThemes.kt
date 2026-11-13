package themebuild

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Renders every palette through the templates into `themes/<Name>.theme.json`,
 * `themes/<Name>Islands.theme.json` and `themes/<Name>.xml`.
 *
 * Templates name colour roles in two ways:
 * - UI keys in `theme.json` / `islands.json` take a bare role name (`"Tree.background": "panelAlt"`).
 * - Anywhere else, including the whole editor scheme, `${role}` is substituted textually.
 *
 * All roles are resolved to literal colours, so the output never depends on how the IDE resolves
 * named colours. The task fails on an unknown role, on a palette role no template uses, and on a
 * generated theme that `plugin.xml` doesn't register.
 */
@CacheableTask
abstract class GenerateThemes : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val palettesDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val templatesDir: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val pluginXml: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val templates = templatesDir.get().asFile
        val themeTemplate = File(templates, "theme.json").readText()
        val islandsTemplate = File(templates, "islands.json").readText()
        val schemeTemplate = File(templates, "scheme.xml").readText()
        val registered = pluginXml.get().asFile.readText()

        val out = File(outputDir.get().asFile, "themes")
        out.deleteRecursively()
        out.mkdirs()

        val problems = mutableListOf<String>()
        for (palette in Palette.loadAll(palettesDir.get().asFile)) {
            val used = mutableSetOf<String>()
            val base = palette.baseName
            val schemePath = "/themes/$base.xml"

            val theme = LinkedHashMap<String, Any?>()
            theme["name"] = palette.name
            theme["dark"] = palette.dark
            theme["editorScheme"] = schemePath
            theme.putAll(parseJson(substitute(themeTemplate, palette, used, jsonColor), "theme.json"))
            resolveUi(theme, palette, used, "theme.json")

            val islands = deepCopy(theme)
            val overlay = parseJson(substitute(islandsTemplate, palette, used, jsonColor), "islands.json")
            resolveUi(overlay, palette, used, "islands.json")
            overlay.forEach { (key, value) ->
                if (key == "ui") {
                    @Suppress("UNCHECKED_CAST")
                    (islands["ui"] as MutableMap<String, Any?>).putAll(value as Map<String, Any?>)
                } else {
                    islands[key] = value
                }
            }
            islands["name"] = "${palette.name} Islands"

            val scheme = substitute(schemeTemplate, palette, used, xmlColor)

            write(File(out, "$base.theme.json"), theme)
            write(File(out, "${base}Islands.theme.json"), islands)
            File(out, "$base.xml").writeText(scheme)

            val unused = palette.colors.keys - used
            if (unused.isNotEmpty()) {
                problems += "${palette.file.name}: roles not used by any template: ${unused.joinToString()}"
            }
            for (path in listOf("/themes/$base.theme.json", "/themes/${base}Islands.theme.json")) {
                if ("path=\"$path\"" !in registered) {
                    problems += "plugin.xml has no <themeProvider path=\"$path\"/> for palette ${palette.file.name}"
                }
            }
        }
        if (problems.isNotEmpty()) throw GradleException(problems.joinToString("\n"))
    }

    private val jsonColor: (String) -> String = { it }

    private val xmlColor: (String) -> String = { hex ->
        require(hex.length == 7) { "editor scheme colours must be opaque, got $hex" }
        hex.substring(1).lowercase()
    }

    /** Template variables that depend on the palette rather than naming a colour. */
    private fun variables(palette: Palette) = mapOf(
        "schemeName" to palette.name,
        "schemeParent" to if (palette.dark) "Darcula" else "Default",
        "islandsParent" to if (palette.dark) "ExperimentalDark" else "ExperimentalLightWithLightHeader",
        // Icon palette keys carry a ".Dark" suffix in dark themes only.
        "iconSuffix" to if (palette.dark) ".Dark" else "",
    )

    private fun substitute(text: String, palette: Palette, used: MutableSet<String>, format: (String) -> String): String {
        val variables = variables(palette)
        return Regex("""\$\{(\w+)}""").replace(text) { match ->
            val name = match.groupValues[1]
            variables[name] ?: run {
                used += name
                format(palette.color(name, "\${$name} in a template"))
            }
        }
    }

    /** Replaces bare role names in the `ui` section (at any depth) with their colours. */
    private fun resolveUi(theme: MutableMap<String, Any?>, palette: Palette, used: MutableSet<String>, template: String) {
        @Suppress("UNCHECKED_CAST")
        fun walk(map: MutableMap<String, Any?>, path: String) {
            for ((key, value) in map.entries.toList()) {
                when (value) {
                    is MutableMap<*, *> -> walk(value as MutableMap<String, Any?>, "$path.$key")
                    is String -> if (!value.startsWith("#")) {
                        used += value
                        map[key] = palette.color(value, "$template $path.$key")
                    }
                }
            }
        }
        @Suppress("UNCHECKED_CAST")
        (theme["ui"] as MutableMap<String, Any?>?)?.let { walk(it, "ui") }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseJson(text: String, template: String): MutableMap<String, Any?> =
        try {
            JsonSlurper().parseText(text) as MutableMap<String, Any?>
        } catch (e: Exception) {
            throw GradleException("$template: ${e.message}", e)
        }

    @Suppress("UNCHECKED_CAST")
    private fun deepCopy(map: Map<String, Any?>): MutableMap<String, Any?> =
        map.mapValuesTo(LinkedHashMap()) { (_, v) -> if (v is Map<*, *>) deepCopy(v as Map<String, Any?>) else v }

    private fun write(file: File, json: Map<String, Any?>) {
        file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(json)) + "\n")
    }
}
