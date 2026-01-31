import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

/**
 * Plugin that configures inspequte tasks for all source sets in a project.
 * This plugin creates tasks for writing inspequte inputs and running inspequte
 * analysis for both 'main' and 'test' source sets.
 */
class InspequtePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Get the inspequte availability check
        val inspequteAvailable = project.providers.of(InspequteAvailableValueSource::class.java) {}

        // Configure tasks for all source sets using lazy configuration APIs
        project.plugins.withType(JavaBasePlugin::class.java).configureEach {
            val javaExtension = project.extensions.getByType<JavaPluginExtension>()
            
            javaExtension.sourceSets.configureEach {
                configureInspequteForSourceSet(project, this, inspequteAvailable)
            }
        }
    }

    private fun configureInspequteForSourceSet(
        project: Project,
        sourceSet: SourceSet,
        inspequteAvailable: Provider<Boolean>
    ) {
        // Generate proper task names using SourceSet.getTaskName
        val writeInputsTaskName = sourceSet.getTaskName("writeInspequteInputs", null)
        val inspequteTaskName = sourceSet.getTaskName("inspequte", null)
        
        // Task to write inspequte input files
        val writeInputsTask = project.tasks.register<WriteInspequteInputsTask>(writeInputsTaskName) {
            this.sourceSet.set(sourceSet)
            dependsOn(project.tasks.named(sourceSet.classesTaskName))
            group = "verification"
            description = "Writes inspequte input files for ${sourceSet.name} source set"
        }

        // Task to run inspequte
        val inspequteTask = project.tasks.register<Exec>(inspequteTaskName) {
            dependsOn(writeInputsTask)
            group = "verification"
            description = "Runs inspequte analysis for ${sourceSet.name} source set"
            
            val buildDir = project.layout.buildDirectory
            inputs.files(
                buildDir.file("inspequte/${sourceSet.name}/inputs.txt"),
                buildDir.file("inspequte/${sourceSet.name}/classpath.txt")
            )
            outputs.file(buildDir.file("inspequte-${sourceSet.name}.sarif"))

            // Validate that inspequte is available
            doFirst {
                if (!inspequteAvailable.get()) {
                    throw GradleException(
                        "inspequte executable not found in PATH. " +
                            "Please install it using: cargo install inspequte --locked"
                    )
                }
            }

            val buildDirPath = buildDir.get()
            commandLine(
                "inspequte",
                "--input",
                "@$buildDirPath/inspequte/${sourceSet.name}/inputs.txt",
                "--classpath",
                "@$buildDirPath/inspequte/${sourceSet.name}/classpath.txt",
                "--output",
                "$buildDirPath/inspequte-${sourceSet.name}.sarif"
            )
        }

        // Make the check task depend on inspequte task
        // The check task is guaranteed to exist because we're inside withType(JavaBasePlugin)
        project.tasks.named("check") {
            dependsOn(inspequteTask)
        }
    }
}

/**
 * Task to write inspequte input files (inputs.txt and classpath.txt).
 */
abstract class WriteInspequteInputsTask : DefaultTask() {
    @get:org.gradle.api.tasks.Internal
    abstract val sourceSet: org.gradle.api.provider.Property<SourceSet>

    @get:InputFiles
    val classDirectories: org.gradle.api.file.FileCollection
        get() = sourceSet.get().output.classesDirs

    @get:InputFiles
    val runtimeClasspath: org.gradle.api.file.FileCollection
        get() = sourceSet.get().runtimeClasspath

    @get:OutputFile
    val inputsFile: org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>
        get() {
            val sourceSetName = sourceSet.get().name
            return project.layout.buildDirectory.file("inspequte/$sourceSetName/inputs.txt")
        }

    @get:OutputFile
    val classpathFile: org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>
        get() {
            val sourceSetName = sourceSet.get().name
            return project.layout.buildDirectory.file("inspequte/$sourceSetName/classpath.txt")
        }

    @TaskAction
    fun writeInputs() {
        val inputsFileObj = inputsFile.get().asFile
        val classpathFileObj = classpathFile.get().asFile
        
        // Create parent directories
        inputsFileObj.parentFile.mkdirs()
        classpathFileObj.parentFile.mkdirs()

        // Write class directories to inputs.txt
        inputsFileObj.writeText(
            classDirectories.files.joinToString("\n")
        )

        // Write classpath to classpath.txt
        classpathFileObj.writeText(
            runtimeClasspath.files.joinToString("\n")
        )
    }
}
