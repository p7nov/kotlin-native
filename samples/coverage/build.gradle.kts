import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
    }

    macosX64("macos") {
        binaries {
            executable(listOf(DEBUG)) {
                entryPoint = "coverage.main"
            }
        }
        binaries.getExecutable("test", DEBUG).apply {
            freeCompilerArgs = mutableListOf(
                    "-Xlibrary-to-cover=${compilations["main"].output.classesDirs.singleFile.absolutePath}"
            )
        }
    }
}

tasks.create("createCoverageReport") {
    dependsOn("macosTest")

    description = "Create coverage report"

    // TODO: use tools from the distribution
    doLast {
        exec {
            commandLine("llvm-profdata", "merge", "default.profraw", "-o", "program.profdata")
        }
        exec {
            val testDebugBinary = kotlin.targets["macos"].let { it as KotlinNativeTarget }.binaries.getExecutable("test", "debug").outputFile
            commandLine("llvm-cov", "show", "$testDebugBinary", "-instr-profile", "program.profdata")
        }
    }
}