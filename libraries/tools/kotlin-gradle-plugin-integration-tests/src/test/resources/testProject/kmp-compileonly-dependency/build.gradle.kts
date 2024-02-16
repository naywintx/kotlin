plugins {
    kotlin("multiplatform")
}

repositories {
    mavenLocal()
    mavenCentral()
}

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
                /*
                 * api() is not defined here, to verify that compileOnly() + api() is not necessary
                 * for test compilations, because they are not published
                 */
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
