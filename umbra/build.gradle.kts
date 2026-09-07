plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

minecraft {
    extraRunJvmArguments.add("-Dumbra.dumpClass=true")
    extraRunJvmArguments.add("-Dangelica.sdlgpu.enable=true")
}

apply(from = "../gradle/angelica-run-common.gradle.kts")

apply(from = "../gradle/angelica-shadow-common.gradle.kts")
