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
    //jsTarget: js { browser(); nodejs() }
    //wasmJs: wasmJs { browser() }
    //wasmWasi: wasmWasi { nodejs() }
}

dependencies {
    //compileOnly: commonMainCompileOnly("org.jetbrains.kotlinx:atomicfu:latest.release")
    //api: commonMainApi("org.jetbrains.kotlinx:atomicfu:latest.release")

    //compileOnly: commonMainCompileOnly(project(":my-kmp-lib"))
    //api: commonMainApi(project(":my-kmp-lib"))
}

//jsTarget: kotlin { sourceSets { jsMain { dependencies { api("org.jetbrains.kotlinx:atomicfu:latest.release") } } } }
//jsTarget: kotlin { sourceSets { jsTest { dependencies { api("org.jetbrains.kotlinx:atomicfu:latest.release") } } } }
