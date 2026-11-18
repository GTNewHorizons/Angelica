extensions.getByType<JavaPluginExtension>().apply {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    maven {
        name = "GTNH Maven"
        url = uri("https://nexus.gtnewhorizons.com/repository/public/")
    }
    maven {
        name = "Minecraft Libraries"
        url = uri("https://libraries.minecraft.net")
    }
    mavenCentral()
}
