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

        // Ensure all input directories exist
        mainSourceSet.output.classesDirs.files
            .forEach { it.mkdirs() }

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

    // Validate that inspequte is available
    doFirst {
        try {
            val checkCommand =
                if (System.getProperty("os.name").lowercase().contains("win")) {
                    arrayOf("where", "inspequte")
                } else {
                    arrayOf("which", "inspequte")
                }
            val process = Runtime.getRuntime().exec(checkCommand)
            if (process.waitFor() != 0) {
                throw GradleException(
                    "inspequte executable not found in PATH. " +
                        "Please install it using: cargo install inspequte --locked",
                )
            }
        } catch (e: Exception) {
            throw GradleException(
                "Failed to verify inspequte installation: ${e.message}\n" +
                    "Please install inspequte using: cargo install inspequte --locked",
            )
        }
    }

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
