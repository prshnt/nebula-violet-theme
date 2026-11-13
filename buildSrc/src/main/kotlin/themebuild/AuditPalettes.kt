package themebuild

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
import themebuild.ColorScience.Cvd
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * Measures every palette against `audit-rules.json` and writes a Markdown report per palette.
 *
 * A failing check breaks the build unless the palette lists its id under `audit.knownIssues` with a
 * reason. A known issue that no longer fails also breaks the build, so the list can only shrink.
 * Colour-blindness checks are always measured; they're enforced only for palettes that set
 * `audit.cvdMinDeltaE`.
 */
@CacheableTask
abstract class AuditPalettes : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val palettesDir: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val rules: RegularFileProperty

    @get:OutputDirectory
    abstract val reportDir: DirectoryProperty

    private class Check(
        val id: String,
        val description: String,
        val value: Double,
        val min: Double,
        val enforced: Boolean = true,
    ) {
        val passed get() = value >= min
    }

    @TaskAction
    fun audit() {
        @Suppress("UNCHECKED_CAST")
        val rules = JsonSlurper().parse(rules.get().asFile) as Map<String, Map<String, Any?>>
        val reports = reportDir.get().asFile
        reports.deleteRecursively()
        reports.mkdirs()

        val errors = mutableListOf<String>()
        for (palette in Palette.loadAll(palettesDir.get().asFile)) {
            val checks = runChecks(palette, rules)
            val ids = checks.map { it.id }.toSet()
            val failed = checks.filter { it.enforced && !it.passed }
            val unexplained = failed.filter { it.id !in palette.knownIssues }
            val fixed = checks.filter { it.id in palette.knownIssues && it.passed && it.enforced }
            val unknown = palette.knownIssues.keys - ids

            val report = File(reports, palette.file.nameWithoutExtension + ".md")
            report.writeText(render(palette, checks))
            logger.lifecycle(
                "${palette.name}: ${checks.count { it.enforced }} checks, ${failed.size - unexplained.size} known issues, " +
                    "${unexplained.size} new failures. Report: ${report.toURI()}",
            )
            unexplained.forEach { errors += "${palette.name}: ${it.id} ${it.description} = ${fmt(it.value)}, needs >= ${fmt(it.min)}" }
            fixed.forEach { errors += "${palette.name}: ${it.id} passes now (${fmt(it.value)}); remove it from audit.knownIssues" }
            unknown.forEach { errors += "${palette.name}: audit.knownIssues has '$it', which is not a check id" }
        }
        if (errors.isNotEmpty()) throw GradleException("Palette audit failed:\n" + errors.joinToString("\n"))
    }

    @Suppress("UNCHECKED_CAST")
    private fun runChecks(palette: Palette, rules: Map<String, Map<String, Any?>>): List<Check> {
        fun rgb(role: String) = ColorScience.parse(palette.color(role, "audit-rules.json"))
        fun roles(section: String) = rules.getValue(section)["roles"] as List<String>
        fun min(section: String, key: String) = (rules.getValue(section)[key] as Number).toDouble()
        val background = rules.getValue("text")["background"] as String

        val checks = mutableListOf<Check>()
        for (role in roles("text")) {
            checks += Check(
                "contrast:$role/$background", "WCAG contrast",
                ColorScience.wcagContrast(rgb(role), rgb(background)), min("text", "minWcag"),
            )
        }
        val subtleBackground = rules.getValue("subtle")["background"] as String
        for (role in roles("subtle")) {
            checks += Check(
                "apca:$role/$subtleBackground", "APCA Lc (absolute)",
                abs(ColorScience.apcaLc(rgb(role), rgb(subtleBackground))), min("subtle", "minApcaLc"),
            )
        }
        for ((fg, bg) in rules.getValue("pairs")["pairs"] as List<List<String>>) {
            checks += Check("contrast:$fg/$bg", "WCAG contrast", ColorScience.wcagContrast(rgb(fg), rgb(bg)), min("pairs", "minWcag"))
        }

        // Colours that must read as different categories, compared only within their group.
        val groups = rules.getValue("distinct")["groups"] as Map<String, List<String>>
        val pairs = groups.values.flatMap { group -> group.flatMapIndexed { i, a -> group.drop(i + 1).map { b -> a to b } } }
        for ((a, b) in pairs) {
            checks += Check("deltaE:$a/$b", "CIEDE2000", ColorScience.deltaE2000(rgb(a), rgb(b)), min("distinct", "minDeltaE"))
        }
        for (cvd in Cvd.entries) {
            for ((a, b) in pairs) {
                checks += Check(
                    "deltaE-${cvd.name.lowercase()}:$a/$b", "CIEDE2000 under ${cvd.label}",
                    ColorScience.deltaE2000(cvd.simulate(rgb(a)), cvd.simulate(rgb(b))),
                    palette.cvdMinDeltaE ?: min("distinct", "minDeltaE"),
                    enforced = palette.cvdMinDeltaE != null,
                )
            }
        }
        return checks
    }

    private fun render(palette: Palette, checks: List<Check>): String = buildString {
        appendLine("# ${palette.name}: palette audit")
        appendLine()
        appendLine("Generated by `./gradlew auditPalettes` from `${palette.file.name}`. Rules: `src/theme/audit-rules.json`.")
        appendLine()
        val sections = checks.groupBy { check ->
            when {
                check.id.startsWith("contrast:") || check.id.startsWith("apca:") -> "Contrast"
                check.id.startsWith("deltaE:") -> "Distinctness (normal vision)"
                else -> "Distinctness under " + check.description.substringAfter("under ")
            }
        }
        for ((title, list) in sections) {
            val enforced = list.first().enforced
            appendLine("## $title" + if (enforced) "" else " (measured, not enforced)")
            appendLine()
            appendLine("| Check | Measure | Value | Minimum | Result |")
            appendLine("|---|---|---:|---:|---|")
            // Worst first, so problems are at the top of each table.
            for (check in list.sortedBy { it.value / it.min }) {
                val result = when {
                    check.passed -> "pass"
                    !check.enforced -> "below"
                    check.id in palette.knownIssues -> "known: ${palette.knownIssues[check.id]}"
                    else -> "**FAIL**"
                }
                appendLine("| `${check.id}` | ${check.description} | ${fmt(check.value)} | ${fmt(check.min)} | $result |")
            }
            appendLine()
        }
    }

    private fun fmt(value: Double) = String.format(Locale.ROOT, "%.2f", value)
}
