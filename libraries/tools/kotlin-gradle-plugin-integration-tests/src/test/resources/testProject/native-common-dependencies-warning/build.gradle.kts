plugins {
    kotlin("multiplatform")
}

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    linuxX64()
    mingwX64()
    macosX64()
    //jsTarget: js { browser() }
    wasmJs()
}

// TODO remove this project, it was renamed to kmp-compileonly-dependency

dependencies {
    //compileOnly: commonMainCompileOnly("org.jetbrains.kotlin:kotlin-stdlib")
    //api: commonMainApi("org.jetbrains.kotlin:kotlin-stdlib")
}
