plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.diffplug.spotless")
    id("com.google.devtools.ksp")
    id("test-report-aggregation")
}

sourceSets.main {
    // KSP - To use generated sources
    java.srcDirs("build/generated/ksp/main/kotlin")
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

val kotest = "5.8.0"
val koin = "3.5.0"
val koinKsp = "1.3.0"
dependencies {
    implementation("io.insert-koin:koin-core:$koin")
    compileOnly("io.insert-koin:koin-annotations:$koinKsp")
    ksp("io.insert-koin:koin-ksp-compiler:$koinKsp")
    testImplementation("io.kotest:kotest-assertions-core:$kotest")
    testImplementation("io.kotest:kotest-property:$kotest")
    testImplementation("io.kotest:kotest-runner-junit5:$kotest")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
