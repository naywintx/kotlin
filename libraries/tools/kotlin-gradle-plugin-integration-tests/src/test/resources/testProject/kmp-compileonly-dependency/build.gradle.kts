plugins {
    kotlin("multiplatform")
}

repositories {
    mavenLocal()
    mavenCentral()
}

// TODO delete this dir, tests were converted to functional tests

kotlin {
    jvm()

    linuxX64()
    mingwX64()
    macosX64()

    js { browser() }

    wasmJs { browser() }
    wasmWasi { nodejs() }

    sourceSets {
        commonMain {
            dependencies {
                //commonMain-compileOnly: compileOnly("org.jetbrains.kotlinx:atomicfu:latest.release")
                //commonMain-api: api("org.jetbrains.kotlinx:atomicfu:latest.release")
            }
        }
        commonTest {
            dependencies {
                //commonTest-compileOnly: compileOnly("org.jetbrains.kotlinx:atomicfu:latest.release")
            }
        }
        jvmMain {
            dependencies {
                // JVM does not require exposing `compileOnly()` dependencies
            }
        }
        jsMain {
            dependencies {
                //targetMain-api: api("org.jetbrains.kotlinx:atomicfu:latest.release")
            }
        }
        nativeMain {
            dependencies {
                //targetMain-api: api("org.jetbrains.kotlinx:atomicfu:latest.release")
            }
        }
        wasmJsMain {
            dependencies {
                //targetMain-api: api("org.jetbrains.kotlinx:atomicfu:latest.release")
            }
        }
        wasmWasiMain {
            dependencies {
                //targetMain-api: api("org.jetbrains.kotlinx:atomicfu:latest.release")
            }
        }
    }
}
