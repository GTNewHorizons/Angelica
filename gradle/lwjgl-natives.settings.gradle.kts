// Applied from each build's settings file. Injects lwjglNatives
val osName = System.getProperty("os.name").lowercase()
val osArch = System.getProperty("os.arch").lowercase()

val natives = when {
    osName.contains("linux") && osArch.contains("aarch64") -> "natives-linux-arm64"
    osName.contains("linux") -> "natives-linux"
    osName.contains("windows") && osArch.contains("aarch64") -> "natives-windows-arm64"
    osName.contains("windows") -> "natives-windows"
    osName.contains("mac") && osArch.contains("aarch64") -> "natives-macos-arm64"
    osName.contains("mac") -> "natives-macos"
    else -> "natives-linux"
}

gradle.rootProject {
    allprojects {
        extra["lwjglNatives"] = natives
    }
}
