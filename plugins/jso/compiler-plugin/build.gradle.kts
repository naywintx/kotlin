import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsCompilerAttribute

description = "JavaScript Object Compiler Plugin"

plugins {
    kotlin("jvm")
    id("jps-compatible")
}

val jsoIrRuntimeForTests by configurations.creating {
    attributes {
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.js)
        attribute(KotlinJsCompilerAttribute.jsCompilerAttribute, KotlinJsCompilerAttribute.ir)
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(KotlinUsages.KOTLIN_RUNTIME))
    }
}

dependencies {
    embedded(project(":plugins:jso:compiler-plugin:jso.common")) { isTransitive = false }
    embedded(project(":plugins:jso:compiler-plugin:jso.k2")) { isTransitive = false }
    embedded(project(":plugins:jso:compiler-plugin:jso.backend")) { isTransitive = false }
    embedded(project(":plugins:jso:compiler-plugin:jso.cli")) { isTransitive = false }

    testApi(project(":compiler:backend"))
    testApi(project(":compiler:cli"))
    testApi(project(":plugins:jso:compiler-plugin:jso.cli"))

    testApi(projectTests(":compiler:test-infrastructure"))
    testApi(projectTests(":compiler:test-infrastructure-utils"))
    testApi(projectTests(":compiler:tests-compiler-utils"))
    testApi(projectTests(":compiler:tests-common-new"))

    testImplementation(projectTests(":js:js.tests"))
    testImplementation(projectTests(":generators:test-generator"))

    testApi(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)

    if (!project.kotlinBuildProperties.isInJpsBuildIdeaSync) {
        jsoIrRuntimeForTests(project(":plugins:jso:runtime")) { isTransitive = false }

        embedded(project(":plugins:jso:runtime")) {
            attributes {
                attribute(KotlinPlatformType.attribute, KotlinPlatformType.js)
                attribute(KotlinJsCompilerAttribute.jsCompilerAttribute, KotlinJsCompilerAttribute.ir)
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(KotlinUsages.KOTLIN_RUNTIME))
            }
            isTransitive = false
        }
    }

    testRuntimeOnly(project(":core:descriptors.runtime"))
}

optInToExperimentalCompilerApi()

sourceSets {
    "main" { none() }
    "test" {
        projectDefault()
        generatedTestDir()
    }
}

publish()

runtimeJar()
sourcesJar()
javadocJar()
testsJar()

projectTest(parallel = true, jUnitMode = JUnitMode.JUnit5) {
    useJUnitPlatform()
    useJsIrBoxTests(version = version, buildDir = layout.buildDirectory)

    workingDir = rootDir

    dependsOn(jsoIrRuntimeForTests)

    val localJsoIrRuntimePath: FileCollection = jsoIrRuntimeForTests

    doFirst {
        systemProperty("jso.runtime.path", localJsoIrRuntimePath.asPath)
    }
}

val generateTests by generator("org.jetbrains.kotlinx.jso.TestGeneratorKt")
