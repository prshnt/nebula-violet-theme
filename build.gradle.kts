import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import themebuild.AuditPalettes
import themebuild.GenerateThemes

plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog") version "2.5.0"
}

dependencies {
    intellijPlatform {
        // A theme has no code, so the build target only matters for verification.
        intellijIdea("2024.3")
    }
}

intellijPlatform {
    // No settings or code to index; skip the headless IDE run.
    buildSearchableOptions = false

    pluginConfiguration {
        version = project.version.toString()
        // Every released version from CHANGELOG.md, newest first: a plain "1.1.0" heading, then
        // its Added/Changed/Fixed groups one level below.
        changeNotes = provider {
            with(changelog) {
                getAll().values
                    .filter { it.version != unreleasedTerm.get() }
                    .joinToString("\n") { item ->
                        val body = renderItem(
                            item.withHeader(false).withLinks(false).withEmptySections(false),
                            Changelog.OutputType.HTML,
                        )
                        "<h3>${item.version}</h3>\n" + body.replace("<h3>", "<h4>").replace("</h3>", "</h4>")
                    }
            }
        }
        ideaVersion {
            // 241 = 2024.1. The Islands variant needs 2025.3 (253) or newer to render as
            // islands; on older IDEs the extra keys are ignored and it looks like a plain
            // new-UI theme.
            sinceBuild = "241"
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            // The oldest IDE `sinceBuild` allows, so the 241 floor is actually tested.
            create(IntelliJPlatformType.IntellijIdea, "2024.1")
            current()
            latest {
                types = listOf(IntelliJPlatformType.IntellijIdea)
                channels = listOf(ProductRelease.Channel.RELEASE)
            }
        }
    }

    // Marketplace requires a signed plugin. Supply secrets via env vars, never commit them.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    // `./gradlew publishPlugin` works after the first manual upload on the Marketplace.
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

changelog {
    repositoryUrl = "https://github.com/prshnt/nebula-violet-theme"
}

// Every variant is a palette (src/theme/palettes/*.json) rendered through shared templates
// (src/theme/template/) into a classic theme, an Islands theme and an editor color scheme.
val generateThemes = tasks.register<GenerateThemes>("generateThemes") {
    palettesDir = layout.projectDirectory.dir("src/theme/palettes")
    templatesDir = layout.projectDirectory.dir("src/theme/template")
    pluginXml = layout.projectDirectory.file("src/main/resources/META-INF/plugin.xml")
    outputDir = layout.buildDirectory.dir("generated/themes")
}

sourceSets.main {
    resources.srcDir(generateThemes)
}

// Contrast, distinctness and color-blindness checks; see README "Palette audit".
val auditPalettes = tasks.register<AuditPalettes>("auditPalettes") {
    group = "verification"
    description = "Measures every palette against src/theme/audit-rules.json."
    palettesDir = layout.projectDirectory.dir("src/theme/palettes")
    rules = layout.projectDirectory.file("src/theme/audit-rules.json")
    reportDir = layout.buildDirectory.dir("reports/palette-audit")
}

tasks.check { dependsOn(auditPalettes) }
tasks.named("buildPlugin") { dependsOn(auditPalettes) }

tasks.jar {
    from(layout.projectDirectory.file("LICENSE")) {
        into("META-INF")
    }
}

// --- Marketplace screenshots -------------------------------------------------------------
// `./gradlew screenshots` launches a sandbox copy of a locally installed IDE (2025.3+ so the
// Islands variant renders as islands) with the demo project, and writes every theme to
// screenshots/out/. Override the IDE with -PscreenshotIde=/path/to/IDE.app.
val screenshotIde = providers.gradleProperty("screenshotIde")
    .orElse(System.getProperty("user.home") + "/Applications/IntelliJ IDEA.app")

val compileScreenshotHelper = tasks.register<JavaCompile>("compileScreenshotHelper") {
    source = fileTree("screenshots/helper/src")
    classpath = files(screenshotIde.map { fileTree("$it/Contents/lib") { include("*.jar") } })
    destinationDirectory = layout.buildDirectory.dir("screenshot-helper/classes")
    // 2026.x platform classes are Java 25 bytecode.
    javaCompiler = javaToolchains.compilerFor { languageVersion = JavaLanguageVersion.of(25) }
    options.release = 25
}

val screenshotHelperJar = tasks.register<Jar>("screenshotHelperJar") {
    from(compileScreenshotHelper)
    from("screenshots/helper/resources")
    archiveFileName = "screenshot-helper.jar"
    destinationDirectory = layout.buildDirectory.dir("screenshot-helper")
}

// Each palette's two themes, with the provider ids plugin.xml gives them (dev.pkhanal.<palette file>).
val screenshotThemes = layout.projectDirectory.dir("src/theme/palettes").asFile
    .listFiles { f -> f.extension == "json" }.orEmpty()
    .map { it.nameWithoutExtension }.sorted()
    .flatMap { listOf(it, "$it-islands") }

val runIdeForScreenshots = intellijPlatformTesting.runIde.register("runIdeForScreenshots") {
    localPath = screenshotIde.map { file(it) }
    // Copied into the sandbox's plugins dir; never part of the published theme.
    prepareSandboxTask {
        from(screenshotHelperJar) {
            into("nebula-violet-screenshot-helper/lib")
        }
    }
    task {
        val demo = layout.projectDirectory.dir("screenshots/demo").asFile.absolutePath
        val out = layout.projectDirectory.dir("screenshots/out").asFile.absolutePath
        args(demo)
        jvmArgs(
            "-Dshot.out=$out",
            "-Dshot.file=$demo/src/dev/nebula/orbit/MissionControl.java",
            // The caret goes on the first line containing this text, so editing the demo can't
            // silently shift the screenshot.
            "-Dshot.caretLine=case Lander l when",
            "-Dshot.themes=" + screenshotThemes.joinToString(",") { "dev.pkhanal.$it=$it" },
            "-Didea.trust.all.projects=true",
            "-Dide.show.tips.on.startup.default.value=false",
        )
    }
}

tasks.register("screenshots") {
    group = "documentation"
    description = "Renders Marketplace screenshots of every theme into screenshots/out/."
    dependsOn(runIdeForScreenshots.name)
}
