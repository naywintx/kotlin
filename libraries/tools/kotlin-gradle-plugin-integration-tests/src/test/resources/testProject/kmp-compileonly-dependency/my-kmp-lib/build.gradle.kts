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
    //jsTarget: js { browser() }
    //wasmJs: wasmJs { browser() }
    //wasmWasi: wasmWasi { nodejs() }
}
