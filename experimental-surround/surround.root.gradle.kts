import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

dependencies {
    add("shadowImplementation", project(":experimental-surround-runtime")) { isTransitive = false }
    add("mixinAnnotationProcessor", project(":experimental-surround-ap"))
}

tasks.named<ShadowJar>("shadowJar") {
    minimize {
        exclude(project(":experimental-surround-runtime"))
    }
    relocate("com.gtnewhorizons.angelica.experimental.surround", "com.gtnewhorizons.angelica.experimental.surround")
}
