plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    testApi(kotlinStdlib())
    testApi(project(":core:compiler.common"))
    testApi(projectTests(":generators:test-generator"))
    testApi(projectTests(":compiler:test-infrastructure-utils"))
    testApi(projectTests(":compiler:tests-common-new"))
}

sourceSets {
    "main" { }
    "test" { projectDefault() }
}

val generateAndroidTests by generator("org.jetbrains.kotlin.android.tests.CodegenTestsOnAndroidGenerator") {
    workingDir = rootDir

    val destinationDirectory =
        rootProject.file("libraries/tools/kotlin-gradle-plugin-integration-tests/src/test/resources/testProject/CodegenTests").absolutePath

    val testDataDirectories = arrayOf(
        rootProject.file("compiler/testData/codegen/box").absolutePath,
        rootProject.file("compiler/testData/codegen/boxInline").absolutePath,
    )

    args(destinationDirectory, *testDataDirectories)
    dependsOn(":createIdeaHomeForTests")
    systemProperty("idea.home.path", ideaHomePathForTests().get().asFile.canonicalPath)
    systemProperty("idea.use.native.fs.for.win", false)
}
