import java.util.Scanner

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.diffplug.spotless")
    id("io.insert-koin.compiler.plugin")
    id("test-report-aggregation")
}

fun libs(lib: String) =
    project.extensions.getByType<VersionCatalogsExtension>().named("libs").findLibrary(lib).get()

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
    implementation(libs("koin-annotations"))
    testImplementation(libs("kotest-assertions-core"))
    testImplementation(libs("kotest-property"))
    testImplementation(libs("kotest-runner-junit5"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

spotless {
    kotlinGradle {
        ktlint("1.8.0")
    }
    kotlin {
        ktlint("1.8.0")
    }
}
