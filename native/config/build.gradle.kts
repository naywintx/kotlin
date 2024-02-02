plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    api(project(":compiler:config"))
    api(project(":native:kotlin-native-utils"))
}

sourceSets {
    "main" { projectDefault() }
    "test" { none() }
}