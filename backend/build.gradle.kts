plugins {
    id("jp.skypencil.kosmo.kotlin-application-conventions")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("jp.skypencil.kosmo.inspequte-conventions")
}

application {
    mainClass = "jp.skypencil.kosmo.backend.Coordinator"
}

dependencies {
    implementation(libs.slf4j.api)
    implementation(libs.uuid.creator)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    runtimeOnly(libs.log4j.slf4j2.impl)
}
