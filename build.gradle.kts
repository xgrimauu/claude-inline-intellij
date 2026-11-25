import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testImplementation("org.assertj:assertj-core:3.27.6")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // The platform JUnit5TestSessionListener loads junit.framework.TestCase.
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
