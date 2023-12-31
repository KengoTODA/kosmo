plugins {
    id("jp.skypencil.kosmo.kotlin-application-conventions")
}

application {
    mainClass = "jp.skypencil.kosmo.backend.Coordinator"
}

dependencies {
    implementation(libs.slf4j.api)
    implementation(libs.uuid.creator)
    implementation(libs.kotlinx.coroutines.core)
    runtimeOnly(libs.log4j.slf4j2.impl)
}

spotless {
    kotlin {
        targetExclude("build/generated/**/*.kt")
    }
}
