/*
 * Angelica's dependency declarations. Supported configurations:
 *  - api("group:name:version:classifier"): if you use the types from this dependency in the public API of this mod
 *       Available at runtime and compiletime for mods depending on this mod
 *  - implementation("g:n:v:c"): if you need this for internal implementation details of the mod, but none of it is visible via the public API
 *       Available at runtime but not compiletime for mods depending on this mod
 *  - compileOnly("g:n:v:c"): if the mod you're building doesn't need this dependency during runtime at all, e.g. for optional mods
 *       Not available at all for mods depending on this mod, only visible at compiletime for this mod
 *  - runtimeOnlyNonPublishable("g:n:v:c"): if you want to include a mod in this mod's runClient/runServer runs, but not publish it as a dependency
 *  - devOnlyNonPublishable("g:n:v:c"): a combination of runtimeOnlyNonPublishable and compileOnly, not published as a Maven dependency
 *  - shadowImplementation("g:n:v:c"): like api, but included in your jar under a renamed package name
 *  - embedOnly("g:n:v:c"): embedded in the jar without relocation
 *
 * Versions come from gradle/libs.versions.toml. Mod coordinates stay literal: nearly all carry a
 * :dev classifier, which a version catalog cannot express, and curse/modrinth "versions" are file ids.
 *
 * To depend on obfuscated jars use `rfg.deobf("dep:spec:1.2.3")`.
 */
import com.gtnewhorizons.retrofuturagradle.modutils.ModUtils
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.DependencyHandlerScope

val libs = the<LibrariesForLibs>()
val rfg = (dependencies as ExtensionAware).extensions.getByType<ModUtils.RfgDependencyExtension>()

val api = configurations["api"]
val compileOnly = configurations["compileOnly"]
val annotationProcessor = configurations["annotationProcessor"]
val shadowImplementation = configurations["shadowImplementation"]
val runtimeOnlyNonPublishable = configurations["runtimeOnlyNonPublishable"]
val devOnlyNonPublishable = configurations["devOnlyNonPublishable"]

// Mods that can be transformed - used for compiling angelica, but not necessary at runtime
val transformedMod by configurations.creating { isCanBeConsumed = false }
val transformedModCompileOnly by configurations.creating { isCanBeConsumed = false }

// Embed in JAR without relocation (compile only, shadowed into JAR)
val embedOnly by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

// Add the transformed mod dependencies to the compilation classpaths, but don't publish them in the Maven metadata
configurations["compileClasspath"].extendsFrom(transformedMod, transformedModCompileOnly, embedOnly)
configurations["testCompileClasspath"].extendsFrom(transformedMod, transformedModCompileOnly, embedOnly)
configurations["testRuntimeClasspath"].extendsFrom(embedOnly)
configurations["testRuntimeClasspath"].exclude(group = "com.github.GTNewHorizons", module = "lwjgl3ify")

// Prevents transitive Angelica deps from resolving against this project
configurations.configureEach {
    exclude(group = "com.github.GTNewHorizons", module = "Angelica")
}

DependencyHandlerScope.of(dependencies).apply {
    compileOnly("com.github.GTNewHorizons:Hodgepodge:2.7.166:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:Hodgepodge:2.7.166:dev")

    runtimeOnlyNonPublishable(rfg.deobf("curse.maven:biomes-o-plenty-220318:2499612"))

    runtimeOnlyNonPublishable("com.github.GTNewHorizons:Applied-Energistics-2-Unofficial:rv3-beta-994-GTNH:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:ae2stuff:0.10.19-GTNH:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:BetterLoadingScreen:1.7.9-GTNH:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:BetterQuesting:3.8.72-GTNH:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:BuildCraft:7.1.63:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:Botania:1.13.32-GTNH:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:LogisticsPipes:1.5.32-GTNH:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:OpenComputers:1.12.55-GTNH:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:Mobs-Info:0.5.18-GTNH:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:IguanaTweaksTConstruct:2.7.10:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:OpenBlocks:1.12.18-GTNH:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:Schematica:1.12.6-GTNH:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:TinkersConstruct:1.14.93-GTNH:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:ServerUtilities:2.4.1:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:ironchest:6.1.13:dev")
    runtimeOnlyNonPublishable("ganymedes01.etfuturum:Et-Futurum-Requiem:2.6.49-GTNH:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:waila:1.19.30:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:Draconic-Evolution:1.5.30-GTNH:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:HoloInventory:2.5.15-GTNH:dev")

    runtimeOnlyNonPublishable("thaumcraft:Thaumcraft:1.7.10-4.2.3.5:dev")

    compileOnly(libs.retrofuturabootstrap) { isTransitive = false }
    // runtimeOnlyNonPublishable(libs.lwjgl3ify)

    compileOnly(libs.lombok) { isTransitive = false }
    annotationProcessor(libs.lombok)

    // Iris Shaders
    compileOnly(libs.annotations)
    api(libs.gtnhlib) { artifact { classifier = "dev" } }

    shadowImplementation(project(":glsm")) { isTransitive = false }
    shadowImplementation(project(":lwjgl3-backend")) { isTransitive = false }
    shadowImplementation(project(":sdl-gpu")) { isTransitive = false }
    runtimeOnlyNonPublishable(project(":tracy-client")) { isTransitive = false }
    shadowImplementation(libs.eventbus)

    shadowImplementation(libs.jcpp) // Apache 2.0
    shadowImplementation(libs.glsl.transformation.lib) {
        exclude(module = "antlr4") // we only want to shadow the runtime module
    }
    shadowImplementation(libs.antlr4.runtime)
    compileOnly(libs.ant)

    // Celeritas (isTransitive=false: deps provided by GTNHLib/runtime)
    embedOnly(libs.celeritas.common) { isTransitive = false }
    embedOnly(libs.celeritas.lwjgl2.service) { isTransitive = false }

    // Because who doesn't want NEI
    devOnlyNonPublishable("com.github.GTNewHorizons:NotEnoughItems:2.8.109-GTNH:dev")
    devOnlyNonPublishable("com.github.GTNewHorizons:CodeChickenCore:1.4.16:dev")

    // BQ Testing
    // devOnlyNonPublishable("com.github.GTNewHorizons:BetterQuesting:3.8.74-GTNH:dev")

    // Display List Testing
    /*
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:Applied-Energistics-2-Unofficial:rv3-beta-994-GTNH:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:ae2stuff:0.10.19-GTNH:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:BuildCraft:7.1.61:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:IguanaTweaksTConstruct:2.7.10:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:OpenBlocks:1.12.18-GTNH:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:OpenComputers:1.12.47-GTNH:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:TinkersConstruct:1.14.93-GTNH:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:ServerUtilities:2.4.1:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:ironchest:6.1.13:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:waila:1.19.30:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:Botania:1.13.25-GTNH:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:Mobs-Info:0.5.18-GTNH:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:Draconic-Evolution:1.5.27-GTNH:dev")
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:HoloInventory:2.5.15-GTNH:dev")
     */

    //devOnlyNonPublishable("com.github.GTNewHorizons:worldedit-gtnh:v0.0.9")

    // Notfine Deps
    compileOnly("thaumcraft:Thaumcraft:1.7.10-4.2.3.5:dev")
    devOnlyNonPublishable("com.github.GTNewHorizons:Baubles-Expanded:2.2.21-GTNH:dev")
    compileOnly("com.github.GTNewHorizons:twilightforest:2.7.36:dev") { isTransitive = false }
    compileOnly(rfg.deobf("curse.maven:witchery-69673:2234410"))
    compileOnly("com.github.GTNewHorizons:TinkersConstruct:1.14.93-GTNH:dev") { isTransitive = false }
    compileOnly("com.github.GTNewHorizons:Natura:2.8.19:dev")

    compileOnly("com.github.GTNewHorizons:ThaumicHorizons:1.8.20:dev")

    compileOnly("com.github.GTNewHorizons:Battlegear2:1.4.3:dev") { isTransitive = false }

    compileOnly("com.falsepattern:chunkapi-mc1.7.10:0.5.1:api") { isTransitive = false }
    compileOnly("com.falsepattern:endlessids-mc1.7.10:1.5-beta0003:dev") { isTransitive = false }

    compileOnly(rfg.deobf("curse.maven:extrautils-225561:2264383"))
    compileOnly(rfg.deobf("curse.maven:dynamiclights-227874:2337326"))

    compileOnly("curse.maven:minefactory-reloaded-66672:2277486")

    compileOnly("com.github.GTNewHorizons:NotEnoughIds:2.1.11:dev")

    compileOnly(rfg.deobf("maven.modrinth:ntmspace:X5663_H261"))

    compileOnly(rfg.deobf("curse.maven:campfirebackport-387444:4611675"))
    compileOnly(rfg.deobf("curse.maven:xaeros-minimap-263420:5060684"))
    compileOnly(rfg.deobf("curse.maven:security-craft-64760:2818228"))
    compileOnly("ganymedes01.etfuturum:Et-Futurum-Requiem:2.6.49-GTNH:dev")
    compileOnly("com.github.GTNewHorizons:OpenBlocks:1.12.19-GTNH:dev")
    compileOnly("com.github.GTNewHorizons:Chisel:2.17.31-GTNH:dev")
    compileOnly(rfg.deobf("curse.maven:biomes-o-plenty-220318:2499612"))

    devOnlyNonPublishable("com.github.GTNewHorizons:CosmeticArmorReworked:1.0.6-GTNH:dev")

    // For testing alternative splash screens - https://github.com/MalTeeez/ModernSplash/releases
    // devOnlyNonPublishable("com.github.MalTeeez:modernsplash:1.7.10-1.2.4:dev")

    // Hodgepodge
    transformedMod("net.industrial-craft:industrialcraft-2:2.2.828-experimental:dev")

    // Better Crashes
    compileOnly("com.github.GTNewHorizons:BetterCrashes:1.4.5-GTNH:dev")

    // Distant Horizons
    compileOnly("maven.modrinth:DistantHorizonsApi:5.1.0")

    compileOnly("mega:fluidlogged-mc1.7.10:0.1.2")

    compileOnly("com.cardinalstar.cubicchunks:CubicChunks1710:v0.1.5-alpha:dev") { isTransitive = false }
}

afterEvaluate {
    val glsmTestFixtures = configurations["glsmTestFixtures"]
    val testImplementation = configurations["testImplementation"]
    val testCompileOnly = configurations["testCompileOnly"]
    val testRuntimeOnly = configurations["testRuntimeOnly"]

    DependencyHandlerScope.of(dependencies).apply {
        testImplementation(platform(libs.junit.bom))
        testImplementation(libs.junit.jupiter)
        testImplementation(libs.mockito.core)
        testImplementation(libs.lwjgl2)

        testImplementation(libs.junit.platform.engine)
        testImplementation(libs.junit.platform.reporting)
        testImplementation(libs.junit.platform.launcher)

        testImplementation(libs.archunit.junit5)

        glsmTestFixtures(testFixtures(project(":glsm")) as ModuleDependency) { isTransitive = false }
        testCompileOnly(testFixtures(project(":glsm")))
        testImplementation("mega:fluidlogged-mc1.7.10:0.1.2")

        testRuntimeOnly(libs.fastutil)
        testRuntimeOnly(libs.joml)
    }
}
