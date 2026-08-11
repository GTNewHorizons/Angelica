pluginManagement {
    repositories {
        maven {
            // RetroFuturaGradle
            name = "GTNH Maven"
            url = uri("https://nexus.gtnewhorizons.com/repository/public/")
            mavenContent {
                includeGroup("com.gtnewhorizons")
                includeGroupByRegex("com\\.gtnewhorizons\\..+")
            }
        }
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
}

plugins {
    id("com.gtnewhorizons.gtnhsettingsconvention") version ("2.0.27")
}

apply(from = file("../gradle/lwjgl-natives.settings.gradle.kts"))

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

include("glsm")
project(":glsm").projectDir = file("../glsm")

include("lwjgl3-backend")
project(":lwjgl3-backend").projectDir = file("../lwjgl3-backend")
