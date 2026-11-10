import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

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

// The Islands theme is the classic theme plus a small overlay, generated so the palette
// and UI keys live in one place.
val generateIslandsTheme by tasks.registering {
    val base = layout.projectDirectory.file("src/main/resources/themes/NebulaViolet.theme.json")
    val overlay = layout.projectDirectory.file("src/islands/NebulaVioletIslands.overlay.json")
    val outDir = layout.buildDirectory.dir("generated/islands")
    inputs.files(base, overlay)
    outputs.dir(outDir)
    doLast {
        @Suppress("UNCHECKED_CAST")
        val theme = JsonSlurper().parse(base.asFile) as MutableMap<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val extra = JsonSlurper().parse(overlay.asFile) as Map<String, Any?>
        extra.forEach { (key, value) ->
            if (key == "ui") {
                @Suppress("UNCHECKED_CAST")
                (theme["ui"] as MutableMap<String, Any?>).putAll(value as Map<String, Any?>)
            } else {
                theme[key] = value
            }
        }
        val target = outDir.get().file("themes/NebulaVioletIslands.theme.json").asFile
        target.parentFile.mkdirs()
        target.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(theme)) + "\n")
    }
}

sourceSets.main {
    resources.srcDir(generateIslandsTheme)
}

tasks.jar {
    from(layout.projectDirectory.file("LICENSE")) {
        into("META-INF")
    }
}
