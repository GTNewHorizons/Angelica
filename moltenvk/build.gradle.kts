plugins {
    `java-library`
    `maven-publish`
}

version = rootProject.version

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}

val rfgObfAttr = Attribute.of("com.gtnewhorizons.retrofuturagradle.obfuscation", String::class.java)
val rfgTransformedAttr = Attribute.of("rfgDeobfuscatorTransformed", Boolean::class.javaObjectType)

configurations {
    listOf("apiElements", "runtimeElements").forEach { name ->
        named(name) {
            attributes {
                attribute(rfgObfAttr, "mcp")
                attribute(rfgTransformedAttr, true)
            }
        }
    }
}

val moltenvkVersion = (findProperty("moltenvkVersion") as String?) ?: libs.versions.lwjgl3Release.get()

repositories {
    maven {
        name = "Forge"
        url = uri("https://maven.minecraftforge.net/")
    }
    maven {
        name = "Forge artifact-only"
        url = uri("https://maven.minecraftforge.net/")
        metadataSources { artifact() }
        content { includeModule("net.minecraftforge", "forge") }
    }
    mavenCentral()
}

val moltenvkNatives by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    compileOnly("net.minecraftforge:forge:1.7.10-10.13.4.1614-1.7.10:universal") { isTransitive = false }

    listOf("natives-macos", "natives-macos-arm64").forEach { nativesClassifier ->
        moltenvkNatives(libs.lwjgl3.vulkan) {
            artifact { classifier = nativesClassifier }
            isTransitive = false
        }
    }
}

val extractMoltenVK = tasks.register<Sync>("extractMoltenVK") {
    val outDir = layout.buildDirectory.dir("generated/resources/moltenvk")
    from(provider { moltenvkNatives.files.map { zipTree(it) } }) {
        include("macos/**/libMoltenVK.dylib")
    }
    includeEmptyDirs = false
    into(outDir)
    doLast {
        listOf("macos/x64", "macos/arm64").forEach { dir ->
            val rel = "$dir/org/lwjgl/vulkan/libMoltenVK.dylib"
            if (!outDir.get().file(rel).asFile.isFile) {
                throw GradleException("MoltenVK dylib missing after extraction: $rel")
            }
        }
    }
}
sourceSets["main"].resources.srcDir(extractMoltenVK)

val distJar = tasks.register<Jar>("distJar") {
    archiveBaseName.set("angelica-moltenvk")
    archiveVersion.set(moltenvkVersion)
    from(sourceSets["main"].output)
    manifest {
        attributes(
            "FMLCorePlugin" to "com.gtnewhorizons.angelica.moltenvk.loading.MoltenVKCorePlugin"
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "com.gtnewhorizons.angelica"
            artifactId = "angelica-moltenvk"
            version = moltenvkVersion
            artifact(distJar)
        }
    }
    repositories {
        if (hasProperty("publishMoltenVK") && System.getenv("MAVEN_USER") != null) {
            maven {
                name = "GTNHMaven"
                url = uri(rootProject.findProperty("mavenPublishUrl")?.toString() ?: "https://nexus.gtnewhorizons.com/repository/releases/")
                credentials {
                    username = System.getenv("MAVEN_USER")
                    password = System.getenv("MAVEN_PASSWORD")
                }
            }
        }
    }
}
