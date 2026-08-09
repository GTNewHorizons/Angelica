plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

// Re-export Gradle's generated typed catalog accessors so applied scripts (dependencies.gradle.kts) can use them.
dependencies {
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(libs.asm.build)
}
