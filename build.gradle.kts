import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testImplementation("org.assertj:assertj-core:3.27.6")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdea("2026.2.5")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            current()
        }
    }
}

tasks.test {
    useJUnitPlatform()
    inputs.property("e2e", providers.environmentVariable("CLAUDE_INLINE_E2E").orElse(""))
}

intellijPlatformTesting.runIde.register("runIdeProof") {
    task {
        val proof = file("proof")
        val sampleProject = layout.buildDirectory.dir("proof/project")
        val startupScripts = sandboxConfigDirectory.dir("extensions/com.intellij/startup")
        jvmArgumentProviders += CommandLineArgumentProvider {
            listOf(
                "-Dtestscript.filename=${proof.resolve("script.txt")}",
                "-Didea.trust.all.projects=true",
                "-Djb.consents.confirmation.enabled=false",
                "-Djb.privacy.policy.text=<!--999.999-->",
                "-Dide.newUsersOnboarding=false",
                "-Dintellij.startup.wizard=false",
            )
        }
        argumentProviders += CommandLineArgumentProvider { listOf(sampleProject.get().asFile.absolutePath) }
        doFirst {
            sampleProject.get().asFile.deleteRecursively()
            proof.resolve("project").copyRecursively(sampleProject.get().asFile)
            proof.resolve("drive.groovy").copyTo(startupScripts.get().file("drive.groovy").asFile, overwrite = true)
        }
    }
}
