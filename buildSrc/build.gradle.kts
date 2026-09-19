plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.10.2")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.3.5")
}
