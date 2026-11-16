plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// The audit is only as good as its colour maths, so every build runs these tests and fails if they
// do. The root project can't depend on buildSrc tasks, and buildSrc on its own only builds its jar.
// (finalizedBy, not dependsOn: the test classpath already depends on the jar.)
tasks.jar { finalizedBy(tasks.test) }
