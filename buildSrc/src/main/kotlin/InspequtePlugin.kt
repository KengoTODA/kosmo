import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import java.io.File

/**
 * Plugin that configures inspequte tasks for all source sets in a project.
 * This plugin creates tasks for writing inspequte inputs and running inspequte
 * analysis for both 'main' and 'test' source sets.
 */
class InspequtePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Get the inspequte availability check
        val inspequteAvailable = project.providers.of(InspequteAvailableValueSource::class.java) {}

        // Configure tasks for all source sets
        project.afterEvaluate {
            val sourceSets = project.extensions.getByName("sourceSets") as org.gradle.api.tasks.SourceSetContainer
            
            sourceSets.forEach { sourceSet ->
                configureInspequteForSourceSet(project, sourceSet, inspequteAvailable)
            }
        }
    }

    private fun configureInspequteForSourceSet(
        project: Project,
        sourceSet: SourceSet,
        inspequteAvailable: Provider<Boolean>
    ) {
        val sourceSetName = sourceSet.name
        val capitalizedName = sourceSetName.replaceFirstChar { it.uppercase() }
        
        // Task to write inspequte input files
        val writeInputsTask = project.tasks.register<WriteInspequteInputsTask>(
            "writeInspequteInputs$capitalizedName"
        ) {
            this.sourceSet.set(sourceSet)
            dependsOn(project.tasks.named("${sourceSetName}Classes"))
            group = "verification"
            description = "Writes inspequte input files for $sourceSetName source set"
        }

        // Task to run inspequte
        project.tasks.register<Exec>("inspequte$capitalizedName") {
            dependsOn(writeInputsTask)
            group = "verification"
            description = "Runs inspequte analysis for $sourceSetName source set"
            
            val buildDir = project.layout.buildDirectory
            inputs.files(
                buildDir.file("inspequte/$sourceSetName/inputs.txt"),
                buildDir.file("inspequte/$sourceSetName/classpath.txt")
            )
            outputs.file(buildDir.file("inspequte-$sourceSetName.sarif"))

            // Validate that inspequte is available
            doFirst {
                if (!inspequteAvailable.get()) {
                    throw GradleException(
                        "inspequte executable not found in PATH. " +
                            "Please install it using: cargo install inspequte --locked"
                    )
                }
            }

            commandLine(
                "inspequte",
                "--input",
                "@${buildDir.get()}/inspequte/$sourceSetName/inputs.txt",
                "--classpath",
                "@${buildDir.get()}/inspequte/$sourceSetName/classpath.txt",
                "--output",
                "${buildDir.get()}/inspequte-$sourceSetName.sarif"
            )
        }
    }
}

/**
 * Task to write inspequte input files (inputs.txt and classpath.txt).
 */
abstract class WriteInspequteInputsTask : DefaultTask() {
    @get:org.gradle.api.tasks.Internal
    abstract val sourceSet: org.gradle.api.provider.Property<SourceSet>

    @TaskAction
    fun writeInputs() {
        val ss = sourceSet.get()
        val sourceSetName = ss.name
        val buildDir = project.layout.buildDirectory.get().asFile
        val inspequteDir = File(buildDir, "inspequte/$sourceSetName")
        inspequteDir.mkdirs()

        val inputsFile = File(inspequteDir, "inputs.txt")
        val classpathFile = File(inspequteDir, "classpath.txt")

        // Ensure all input directories exist
        ss.output.classesDirs.files.forEach { it.mkdirs() }

        // Write class directories to inputs.txt
        inputsFile.writeText(
            ss.output.classesDirs.files.joinToString("\n")
        )

        // Write classpath to classpath.txt
        val classpathConfig = if (sourceSetName == "main") {
            project.configurations.getByName("runtimeClasspath")
        } else {
            project.configurations.getByName("${sourceSetName}RuntimeClasspath")
        }
        
        classpathFile.writeText(
            classpathConfig.files.joinToString("\n")
        )
    }
}
