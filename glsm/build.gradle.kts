import xyz.wagyourtail.jvmdg.gradle.task.files.DowngradeFiles

plugins {
    `java-library`
    `maven-publish`
}

apply(plugin = "xyz.wagyourtail.jvmdowngrader")

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

// Exclude stubs from all output jars
tasks.withType<Jar>().configureEach {
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
    maven {
        name = "WagYourTail"
        url = uri("https://maven.wagyourtail.xyz/releases")
        content { includeGroup("xyz.wagyourtail.jvmdowngrader") }
    }
    mavenCentral()
}

dependencies {
    // MC compile stubs
    compileOnly(sourceSets["stubs"].output)

    // LWJGL
    compileOnly("org.lwjgl.lwjgl:lwjgl:${property("lwjglVersion")}")
    compileOnly("org.lwjgl.lwjgl:lwjgl_util:${property("lwjglVersion")}")

    // Our Deps
    api("com.github.GTNewHorizons:GTNHLib:${property("gtnhlibVersion")}:dev")
    api("net.minecraftforge:eventbus:${property("eventbusVersion")}")

    compileOnly("org.embeddedt.celeritas:celeritas-common:${property("celeritasVersion")}") { isTransitive = false }
    implementation("org.taumc:glsl-transformation-lib:${property("glslTransformLibVersion")}") { exclude(module = "antlr4") }
    implementation("org.antlr:antlr4-runtime:${property("antlr4RuntimeVersion")}")
    implementation("org.anarres:jcpp:${property("jcppVersion")}")

    compileOnly("org.projectlombok:lombok:${property("lombokVersion")}") { isTransitive = false }
    annotationProcessor("org.projectlombok:lombok:${property("lombokVersion")}")
    compileOnly("org.jetbrains:annotations:${property("jetbrainsAnnotationsVersion")}")
    compileOnly("org.apache.logging.log4j:log4j-api:${property("log4jVersion")}")

    // Deps (normally from MC) - compile only, but needed at test runtime
    compileOnly("com.google.guava:guava:${property("guavaVersion")}")
    testRuntimeOnly("com.google.guava:guava:${property("guavaVersion")}")

    // Test
    testImplementation(sourceSets["stubs"].output)
    testImplementation(platform("org.junit:junit-bom:${property("junitBomVersion")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.lwjgl.lwjgl:lwjgl:${property("lwjglVersion")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.apache.logging.log4j:log4j-core:${property("log4jVersion")}")
    testRuntimeOnly("org.embeddedt.celeritas:celeritas-lwjgl2-service:${property("celeritasVersion")}") { isTransitive = false }
    testRuntimeOnly("xyz.wagyourtail.jvmdowngrader:jvmdowngrader-java-api:${property("jvmDowngraderVersion")}:downgraded-8")
    testRuntimeOnly("org.apache.commons:commons-lang3:${property("commonsLang3Version")}")
}

val depsToDowngrade by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    depsToDowngrade("net.minecraftforge:eventbus:${property("eventbusVersion")}")
    depsToDowngrade("org.embeddedt.celeritas:celeritas-common:${property("celeritasVersion")}") { isTransitive = false }
}

tasks.withType<DowngradeFiles>().configureEach {
    downgradeTo.set(JavaVersion.VERSION_1_8)
    multiReleaseOriginal.set(false)
    multiReleaseVersions.set(emptySet())
}

val downgradeDepsForTest by tasks.registering(DowngradeFiles::class) {
    inputCollection = depsToDowngrade
}

val downgradeMainClasses by tasks.registering(DowngradeFiles::class) {
    inputCollection = sourceSets["main"].output.classesDirs.plus(sourceSets["stubs"].output.classesDirs)
    classpath = sourceSets["main"].compileClasspath
    dependsOn(tasks.named("classes"), tasks.named("stubsClasses"))
}

val downgradeTestClasses by tasks.registering(DowngradeFiles::class) {
    inputCollection = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].compileClasspath
    dependsOn(tasks.named("testClasses"))
}

tasks.test {
    useJUnitPlatform()

    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(8)
    }

    dependsOn(downgradeMainClasses, downgradeTestClasses, downgradeDepsForTest)

    val mainClassesDirs = sourceSets["main"].output.classesDirs
        .plus(sourceSets["stubs"].output.classesDirs)
    val testClassesDirs = sourceSets["test"].output.classesDirs

    val downgradedTest = files(downgradeTestClasses.map { it.outputCollection })
    val downgradedMain = files(downgradeMainClasses.map { it.outputCollection })
    val downgradedDeps = files(downgradeDepsForTest.map { it.outputCollection })

    setTestClassesDirs(downgradedTest)
    classpath = downgradedTest
        .plus(downgradedMain)
        .plus(downgradedDeps)
        .plus(classpath.minus(mainClassesDirs).minus(testClassesDirs).minus(depsToDowngrade))

    val extractNatives = rootProject.tasks.named("extractNatives2")
    dependsOn(extractNatives)
    jvmArgs("-Djava.library.path=${extractNatives.get().property("destinationFolder").let { (it as DirectoryProperty).asFile.get().path }}")
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
