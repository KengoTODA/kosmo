plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.25.0")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.20")
    implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:1.9.20-1.0.14")
}
