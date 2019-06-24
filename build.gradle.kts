plugins {
    kotlin("jvm") version "1.3.40" apply false
}

allprojects {
    repositories {
        jcenter()
    }
}

val clean by tasks.creating(Delete::class) {
    delete(rootProject.buildDir)
}
