package themebuild

import groovy.json.JsonSlurper
import java.io.File

/**
 * One theme variant: a display name and a set of colour roles (`keyword`, `panel`, `caretRow`, ...).
 * Templates refer to roles by name; nothing else in the build hard-codes a colour.
 */
class Palette(
    val file: File,
    val name: String,
    val dark: Boolean,
    /** Role name to `#RRGGBB` or `#RRGGBBAA`, in file order. */
    val colors: Map<String, String>,
    /** Audit check id to the reason it is accepted for now. */
    val knownIssues: Map<String, String>,
    /** When set, syntax roles must also stay this far apart (CIEDE2000) under simulated dichromacy. */
    val cvdMinDeltaE: Double?,
) {
    /** File-name stem, e.g. "Nebula Violet" -> "NebulaViolet". */
    val baseName: String = name.replace(" ", "")

    fun color(role: String, where: String): String =
        colors[role] ?: throw IllegalArgumentException("${file.name}: unknown colour role '$role' ($where)")

    companion object {
        fun loadAll(dir: File): List<Palette> {
            val files = dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
            require(files.isNotEmpty()) { "no palettes in $dir" }
            return files.map(::load)
        }

        @Suppress("UNCHECKED_CAST")
        fun load(file: File): Palette {
            val json = JsonSlurper().parse(file) as Map<String, Any?>
            val colors = json["colors"] as Map<String, String>
            val hex = Regex("#[0-9A-F]{6}([0-9A-F]{2})?")
            colors.forEach { (role, value) ->
                require(hex.matches(value)) { "${file.name}: '$role' must be upper-case #RRGGBB or #RRGGBBAA, got '$value'" }
            }
            val audit = json["audit"] as Map<String, Any?>? ?: emptyMap()
            return Palette(
                file = file,
                name = json["name"] as String,
                dark = json["dark"] as Boolean,
                colors = LinkedHashMap(colors),
                knownIssues = audit["knownIssues"] as Map<String, String>? ?: emptyMap(),
                cvdMinDeltaE = (audit["cvdMinDeltaE"] as Number?)?.toDouble(),
            )
        }
    }
}
