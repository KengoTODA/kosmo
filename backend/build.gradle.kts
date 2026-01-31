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

tasks.register("writeInspequteInputs") {
    dependsOn(tasks.named("classes"))
    val mainSourceSet = sourceSets.main.get()
    inputs.files(mainSourceSet.output.classesDirs, configurations.runtimeClasspath)
    val buildDir = layout.buildDirectory
    outputs.files(
        buildDir.file("inspequte/inputs.txt"),
        buildDir.file("inspequte/classpath.txt"),
    )
    doLast {
        val inputsFile = buildDir.file("inspequte/inputs.txt").get().asFile
        val classpathFile = buildDir.file("inspequte/classpath.txt").get().asFile
        inputsFile.parentFile.mkdirs()
        inputsFile.writeText(
            mainSourceSet.output.classesDirs.files
                .joinToString("\n"),
        )
        classpathFile.writeText(
            configurations.runtimeClasspath
                .get()
                .files
                .joinToString("\n"),
        )
    }
}

tasks.register<Exec>("inspequte") {
    dependsOn(tasks.named("writeInspequteInputs"))
    val buildDir = layout.buildDirectory
    inputs.files(
        buildDir.file("inspequte/inputs.txt"),
        buildDir.file("inspequte/classpath.txt"),
    )
    outputs.file(buildDir.file("inspequte.sarif"))
    commandLine(
        "inspequte",
        "--input",
        "@${buildDir.get()}/inspequte/inputs.txt",
        "--classpath",
        "@${buildDir.get()}/inspequte/classpath.txt",
        "--output",
        "${buildDir.get()}/inspequte.sarif",
    )
}
