plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:7.0.4")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.21")
    implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.1.21-2.0.1")
}
