// Replaces GTNHGradle's GitVersionModule (gtnh.modules.gitVersion = false): same version format, but only
// x.y.z[-suffix] tags count, so additional tags (moltenvk-v*, tracy-v*) on master never become the mod version.

fun git(vararg args: String): String? = try {
    val exec = providers.exec {
        commandLine("git", *args)
        workingDir = rootDir
        isIgnoreExitValue = true
    }
    if (exec.result.get().exitValue == 0) exec.standardOutput.asText.get().trim() else null
} catch (e: Exception) {
    null
}

fun gitVersion(): String {
    val described = git("describe", "--tags", "--long", "--first-parent", "--abbrev=7", "--match=[0-9]*.[0-9]*.[0-9]*", "HEAD")
    val match = described?.let { Regex("(.*)-([0-9]+)-g[0-9a-f]+").matchEntire(it) }
    val tag = match?.groupValues?.get(1)
    val distance = match?.groupValues?.get(2)?.toInt() ?: 0
    val hash = git("rev-parse", "HEAD")?.take(10)?.ifEmpty { null }
    val base = tag ?: hash
    if (base == null) {
        logger.error("No git repository found; set the VERSION environment variable to version this build.")
        return "NO-GIT-TAG-SET"
    }
    val dirty = !git("status", "--porcelain").isNullOrEmpty()
    val branch = (git("branch", "--show-current")?.ifEmpty { null } ?: System.getenv("GIT_BRANCH") ?: "git")
        .removePrefix("origin/")
        .replace(Regex("[^a-zA-Z0-9-]+"), "-")
    return when {
        distance > 0 -> "$base-$branch.$distance+$hash${if (dirty) "-dirty" else ""}"
        dirty -> "$base-$branch+$hash-dirty"
        else -> base
    }
}

extra["modVersion"] = providers.environmentVariable("VERSION").orNull ?: gitVersion()
