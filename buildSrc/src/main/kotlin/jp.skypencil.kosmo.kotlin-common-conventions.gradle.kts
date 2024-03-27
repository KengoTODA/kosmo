import java.util.Scanner

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.diffplug.spotless")
    id("com.google.devtools.ksp")
    id("test-report-aggregation")
}

fun libs(lib: String) =
    project.extensions.getByType<VersionCatalogsExtension>().named("libs").findLibrary(lib).get()

sourceSets.main {
    // KSP - To use generated sources
    java.srcDirs("build/generated/ksp/main/kotlin")
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        Scanner(file("$rootDir/.java-version")).use { scanner ->
            val version = scanner.nextInt()
            languageVersion.set(JavaLanguageVersion.of(version))
        }
    }
}

dependencies {
    implementation(libs("koin-core"))
    compileOnly(libs("koin-annotations"))
    ksp(libs("koin-ksp-compiler"))
    testImplementation(libs("kotest-assertions-core"))
    testImplementation(libs("kotest-property"))
    testImplementation(libs("kotest-runner-junit5"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

spotless {
    kotlinGradle {
        ktlint()
    }
    kotlin {
        ktlint()
    }
}
