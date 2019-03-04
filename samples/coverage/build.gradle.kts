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

    // TODO: use tools from distribution
    doLast {
        exec {
            commandLine("llvm-profdata", "merge", "default.profraw", "-o", "program.profdata")
        }
        exec {
            commandLine("llvm-cov", "show", "$buildDir/bin/macos/testDebugExecutable/test.kexe", "-instr-profile", "program.profdata")
        }
    }
}