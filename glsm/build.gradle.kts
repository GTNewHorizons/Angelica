plugins {
    `java-library`
    `maven-publish`
}

version = rootProject.version

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

// RFG variant attributes: the root Angelica project uses RetroFuturaGradle which expects
// these attributes on dependencies. Without them, Gradle can't resolve the subproject variant.
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

// Compile stubs: MC class signatures needed to resolve GTNHLib's type hierarchy.
// Not included in the published jar — real MC classes are on the classpath at runtime.
sourceSets {
    create("stubs") {
        java.srcDir("src/stubs/java")
    }
}

tasks.named<Jar>("jar") {
    // Exclude stubs from the output jar
    exclude("net/minecraft/**")
}

tasks.named<Jar>("sourcesJar") {
    exclude("net/minecraft/**")
}

repositories {
    maven {
        name = "Mojang"
        url = uri("https://libraries.minecraft.net")
    }
    maven {
        name = "GTNH Maven"
        url = uri("https://nexus.gtnewhorizons.com/repository/public/")
    }
    maven {
        name = "Forge"
        url = uri("https://maven.minecraftforge.net/")
    }
    maven {
        name = "taumc"
        url = uri("https://maven.taumc.org/releases")
    }
    mavenCentral()
}

dependencies {
    // MC compile stubs
    compileOnly(sourceSets["stubs"].output)

    // LWJGL
    compileOnly("org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209")
    compileOnly("org.lwjgl.lwjgl:lwjgl_util:2.9.4-nightly-20150209")

    // Our Deps
    api("com.github.GTNewHorizons:GTNHLib:0.9.36:dev")
    api("net.minecraftforge:eventbus:7.0-beta.15")

    compileOnly("org.embeddedt.celeritas:celeritas-common:2.5.3-GTNH") { isTransitive = false }
    implementation("org.taumc:glsl-transformation-lib:0.2.0-26.g5ee7d96-GTNH") { exclude(module = "antlr4") }
    implementation("org.antlr:antlr4-runtime:4.13.2")
    implementation("org.anarres:jcpp:1.4.14")

    compileOnly("org.projectlombok:lombok:1.18.42") { isTransitive = false }
    annotationProcessor("org.projectlombok:lombok:1.18.42")
    compileOnly("org.jetbrains:annotations:26.0.2")
    compileOnly("org.apache.logging.log4j:log4j-api:2.0-beta9")

    // Deps (normally from MC) - compile only
    compileOnly("com.google.guava:guava:18.0")

    // Test
    testImplementation(platform("org.junit:junit-bom:5.14.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209")
    testRuntimeOnly("org.apache.logging.log4j:log4j-core:2.0-beta9")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "com.gtnewhorizons.angelica"
            artifactId = "glsm"
            version = rootProject.version.toString()
            from(components["java"])
        }
    }
    repositories {
        if (System.getenv("MAVEN_USER") != null) {
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
