import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import themebuild.AuditPalettes
import themebuild.GenerateThemes

plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
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

val compileScreenshotHelper by tasks.registering(JavaCompile::class) {
    source = fileTree("screenshots/helper/src")
    classpath = files(screenshotIde.map { fileTree("$it/Contents/lib") { include("*.jar") } })
    destinationDirectory = layout.buildDirectory.dir("screenshot-helper/classes")
    // 2026.x platform classes are Java 25 bytecode.
    javaCompiler = javaToolchains.compilerFor { languageVersion = JavaLanguageVersion.of(25) }
    options.release = 25
}

val screenshotHelperJar by tasks.registering(Jar::class) {
    from(compileScreenshotHelper)
    from("screenshots/helper/resources")
    archiveFileName = "screenshot-helper.jar"
    destinationDirectory = layout.buildDirectory.dir("screenshot-helper")
}

val runIdeForScreenshots by intellijPlatformTesting.runIde.registering {
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
            "-Dshot.line=37",
            "-Dshot.themes=" + listOf(
                "nebula-violet", "nebula-violet-colorblind", "nebula-violet-light", "nebula-violet-light-colorblind",
                "nebula-violet-midnight", "nebula-violet-midnight-colorblind",
            ).flatMap { listOf(it, "$it-islands") }.joinToString(",") { "dev.pkhanal.$it=$it" },
            "-Didea.trust.all.projects=true",
            "-Dide.show.tips.on.startup.default.value=false",
        )
    }
}

tasks.register("screenshots") {
    group = "documentation"
    description = "Renders Marketplace screenshots of every theme into screenshots/out/."
    dependsOn(runIdeForScreenshots)
}
