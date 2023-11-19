plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.diffplug.spotless")
    id("com.google.devtools.ksp")
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
    testRuntimeOnly("io.kotest:kotest-runner-junit5:$kotest")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
