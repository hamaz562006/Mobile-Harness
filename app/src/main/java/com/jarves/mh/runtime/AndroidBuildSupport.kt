package com.jarves.mh.runtime

import org.json.JSONObject
import java.io.File

enum class AndroidBuildPhase { IDLE, PREPARING, BUILDING, CANCELLING, SUCCEEDED, FAILED, CANCELLED }
enum class AndroidAction { NONE, BUILD, FORCE_BUILD, INSTALL, RUN, BUILD_AND_RUN, CLEAN }
enum class AndroidBuildStage {
    IDLE, PREPARING, RESOLVING, COMPILING, RESOURCES, PACKAGING, FINDING_APK,
    INSTALLING, LAUNCHING, COMPLETE, FAILED, CANCELLING, CANCELLED,
}
enum class AndroidIssueSeverity { INFO, WARNING, ERROR }

enum class AndroidBuildIssueFix {
    NONE,
    /** Download missing Maven coordinates into the local mirror and leave rebuild to the user. */
    FETCH_LOCAL_MIRROR_ARTIFACTS,
}

data class AndroidBuildIssue(
    val severity: AndroidIssueSeverity = AndroidIssueSeverity.ERROR,
    val title: String,
    val detail: String,
    val suggestion: String? = null,
    val filePath: String? = null,
    val line: Int? = null,
    val column: Int? = null,
    /** Optional automated repair supported for this issue. */
    val fixKind: AndroidBuildIssueFix = AndroidBuildIssueFix.NONE,
    /** Maven coordinates (group:artifact:version) when fixKind needs them. */
    val coordinates: List<String> = emptyList(),
)

data class AndroidApkInfo(
    val path: String,
    val fileName: String,
    val sizeBytes: Long,
    val builtAtMillis: Long,
    val packageName: String? = null,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val minSdk: Int? = null,
    val targetSdk: Int? = null,
    val stale: Boolean = false,
    val installed: Boolean = false,
    val installedMatches: Boolean = false,
    /** Gradle module folder name when detectable (e.g. app, feature). */
    val moduleName: String? = null,
    /** Build variant folder under outputs/apk (debug, release, …). */
    val variant: String? = null,
)

enum class AndroidHealthStatus { RUNNING, PASSED, WARNING, FAILED }
enum class AndroidHealthFix { NONE, INSTALL_TOOLS, OPEN_INSTALL_SETTINGS, OPEN_NETWORK_SETTINGS, OPEN_STORAGE_SETTINGS, CLEAN, COPY_LOGCAT_COMMAND, REFRESH }

data class AndroidHealthCheck(
    val id: String,
    val title: String,
    val detail: String,
    val status: AndroidHealthStatus,
    val fix: AndroidHealthFix = AndroidHealthFix.NONE,
)

enum class AndroidLogLevel { VERBOSE, DEBUG, INFO, WARNING, ERROR, FATAL }
data class AndroidLogLine(
    val id: Long,
    val text: String,
    val level: AndroidLogLevel,
    val filePath: String? = null,
    val line: Int? = null,
)

data class AndroidLogcatState(
    val available: Boolean = false,
    val running: Boolean = false,
    val paused: Boolean = false,
    val message: String? = null,
    val packageName: String? = null,
    val lines: List<AndroidLogLine> = emptyList(),
    val minimumLevel: AndroidLogLevel = AndroidLogLevel.VERBOSE,
    val query: String = "",
)

data class AndroidBuildRecord(
    val phase: AndroidBuildPhase = AndroidBuildPhase.IDLE,
    val message: String? = null,
    val log: String = "",
    val startedAtMillis: Long? = null,
    val finishedAtMillis: Long? = null,
    val apkPath: String? = null,
    val apkSizeBytes: Long? = null,
    val action: AndroidAction = AndroidAction.NONE,
    val stage: AndroidBuildStage = AndroidBuildStage.IDLE,
    val issues: List<AndroidBuildIssue> = emptyList(),
    val recentDurationsMillis: List<Long> = emptyList(),
)

internal fun androidGradleCommand(root: File, task: String): String {
    val launcher = File(root, "gradlew").takeIf(File::isFile)?.let { "bash ./gradlew" } ?: "gradle"
    return "$launcher -Dorg.gradle.jvmargs= --no-daemon --max-workers=2 " +
        "--init-script /root/.gradle/init.d/pocketdev-android.gradle " +
        "-Pandroid.aapt2FromMavenOverride=/root/android-sdk/build-tools/35.0.0/aapt2 " +
        // Gradle daemons can remain attached to deleted PRoot paths after an
        // interrupted build. A bounded, no-daemon invocation is more reliable
        // on Android and avoids leaving a second busy daemon behind.
        "$task --console=plain --stacktrace"
}

internal fun findAndroidProjectRoot(workspace: File): File? {
    val settingsNames = setOf("settings.gradle", "settings.gradle.kts", "settings.gradle.dcl")
    return workspace.walkTopDown().maxDepth(4)
        .filter { it.isFile && it.name in settingsNames }
        .mapNotNull(File::getParentFile)
        .sortedBy { it.absolutePath.length }
        .firstOrNull { root ->
            root.walkTopDown().maxDepth(5)
                .any { it.isFile && it.invariantSeparatorsPath.endsWith("src/main/AndroidManifest.xml") }
        }
}

/**
 * All installable APK outputs under the project (any module / variant).
 * Prefers files listed in output-metadata.json, then falls back to *.apk under outputs/apk/.
 * Deduplicated by absolute path; newest first.
 */
internal fun findDebugApks(projectRoot: File): List<File> {
    val found = LinkedHashMap<String, File>()
    projectRoot.walkTopDown().maxDepth(12)
        .filter { it.isFile && it.name == "output-metadata.json" && "/outputs/apk/" in it.invariantSeparatorsPath }
        .forEach { metadata ->
            runCatching {
                val json = JSONObject(metadata.readText())
                val elements = json.optJSONArray("elements") ?: return@runCatching
                for (i in 0 until elements.length()) {
                    val name = elements.optJSONObject(i)?.optString("outputFile")?.takeIf { it.isNotBlank() } ?: continue
                    val file = File(metadata.parentFile, name)
                    if (file.isFile && file.length() > 0L) {
                        found[file.absolutePath] = file
                    }
                }
            }
        }
    projectRoot.walkTopDown().maxDepth(14)
        .filter {
            it.isFile &&
                it.extension.equals("apk", true) &&
                it.length() > 0L &&
                "/outputs/apk/" in it.invariantSeparatorsPath &&
                "/intermediates/" !in it.invariantSeparatorsPath
        }
        .forEach { found[it.absolutePath] = it }
    return found.values.sortedByDescending { it.lastModified() }
}

/** Most recently modified installable APK (compat helper). */
internal fun findDebugApk(projectRoot: File): File? = findDebugApks(projectRoot).firstOrNull()

/** Module name and variant inferred from a typical Gradle outputs path. */
internal fun apkModuleAndVariant(projectRoot: File, apk: File): Pair<String?, String?> {
    val rel = runCatching { apk.canonicalFile.relativeTo(projectRoot.canonicalFile).invariantSeparatorsPath }
        .getOrElse { apk.absolutePath }
    // …/<module>/build/outputs/apk/<variant>/file.apk
    val parts = rel.split('/')
    val outputsIdx = parts.indexOf("outputs")
    val buildIdx = parts.indexOf("build")
    val module = when {
        buildIdx > 0 -> parts[buildIdx - 1]
        else -> null
    }
    val variant = when {
        outputsIdx >= 0 && parts.getOrNull(outputsIdx + 1) == "apk" -> parts.getOrNull(outputsIdx + 2)
        else -> null
    }
    return module to variant
}

/** Returns the last debug APK only when every build input is older than it. */
internal fun findReusableDebugApk(projectRoot: File): File? {
    val apk = findDebugApk(projectRoot) ?: return null
    val ignoredDirectories = setOf("build", ".gradle", ".idea", ".git")
    val buildInputExtensions = setOf(
        "kt", "java", "xml", "gradle", "kts", "properties", "toml", "pro", "json", "jar", "aar",
    )
    val changedAfterApk = projectRoot.walkTopDown()
        .onEnter { directory -> directory == projectRoot || directory.name !in ignoredDirectories }
        .filter { file ->
            if (!file.isFile) return@filter false
            val relative = file.relativeTo(projectRoot).invariantSeparatorsPath
            val isAndroidSource = relative.startsWith("src/") || "/src/" in relative
            isAndroidSource || file.extension.lowercase() in buildInputExtensions ||
                file.name in setOf("gradlew", "gradlew.bat")
        }
        .any { it.lastModified() > apk.lastModified() }
    return apk.takeUnless { changedAfterApk }
}

internal fun isDebugApkStale(projectRoot: File, apk: File): Boolean = findReusableDebugApk(projectRoot) != apk

internal fun inferAndroidBuildStage(output: String, current: AndroidBuildStage): AndroidBuildStage {
    val text = output.lowercase()
    return when {
        "packagedebug" in text || "assembledebug" in text && "> task" in text -> AndroidBuildStage.PACKAGING
        "merge" in text && "resource" in text || "processdebugresources" in text || "aapt2" in text -> AndroidBuildStage.RESOURCES
        "compiledebugkotlin" in text || "compiledebugjava" in text || "kotlinc" in text || "javac" in text -> AndroidBuildStage.COMPILING
        "download" in text || "resolve" in text || "dependency" in text -> AndroidBuildStage.RESOLVING
        else -> current
    }
}

internal fun parseAndroidBuildIssues(output: String, projectRoot: File, exitCode: Int): List<AndroidBuildIssue> {
    val issues = mutableListOf<AndroidBuildIssue>()

    // Source file errors (Kotlin/Java/XML/Gradle) with location
    val location = Regex(
        """(?m)^(.+?\.(?:kt|java|xml|gradle|kts)):(\d+)(?::(\d+))?[: ]+(?:error: )?(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    location.findAll(output).take(12).forEach { match ->
        val rawPath = match.groupValues[1].trim()
        val file = File(rawPath)
        val relative = when {
            file.isAbsolute && runCatching {
                file.canonicalFile.toPath().startsWith(projectRoot.canonicalFile.toPath())
            }.getOrDefault(false) ->
                file.canonicalFile.relativeTo(projectRoot.canonicalFile).invariantSeparatorsPath
            rawPath.startsWith("/workspace/") ->
                rawPath.substringAfter("/workspace/").substringAfter('/', "")
            else -> rawPath.takeIf { File(projectRoot, it).isFile }
        }
        val detail = match.groupValues[4].trim().take(400)
        issues += AndroidBuildIssue(
            title = classifySourceErrorTitle(detail),
            detail = detail,
            suggestion = "Open the reported file and correct the error on that line.",
            filePath = relative,
            line = match.groupValues[2].toIntOrNull(),
            column = match.groupValues[3].toIntOrNull(),
        )
    }
    if (issues.isNotEmpty()) return issues.distinctBy { Triple(it.filePath, it.line, it.detail) }

    // Dependency / repository resolution (evidence-based, not "internet is down")
    issues += parseDependencyResolutionIssues(output)

    // Plugin resolution
    issues += parsePluginResolutionIssues(output)

    // Android-specific pipeline failures
    issues += parseAndroidPipelineIssues(output)

    // Execution environment failures
    issues += parseExecutionIssues(output)

    if (issues.isNotEmpty()) {
        return issues.distinctBy { Triple(it.title, it.detail.take(120), it.suggestion) }.take(8)
    }

    val diagnosis = diagnoseAndroidBuildFailureDetailed(output, exitCode)
    return listOf(
        AndroidBuildIssue(
            title = diagnosis.title,
            detail = diagnosis.detail,
            suggestion = diagnosis.suggestion,
        ),
    )
}

private fun classifySourceErrorTitle(detail: String): String {
    val d = detail.lowercase()
    return when {
        "unresolved reference" in d -> "Unresolved reference"
        "type mismatch" in d || "type inference" in d -> "Type error"
        "expecting" in d || "syntax error" in d -> "Syntax error"
        else -> "Source error"
    }
}

internal data class Diagnosis(val title: String, val detail: String, val suggestion: String?)

internal fun diagnoseAndroidBuildFailure(output: String, exitCode: Int): String {
    // Backwards-compatible summary string for existing callers/tests.
    // Include title so codes like HTTP 403 are visible (detail alone may omit them).
    val d = diagnoseAndroidBuildFailureDetailed(output, exitCode)
    return buildString {
        append(d.title)
        if (d.detail.isNotBlank() && d.detail != d.title) {
            append(": ")
            append(d.detail)
        }
        if (d.suggestion != null) {
            append(' ')
            append(d.suggestion)
        }
    }.trim()
}

internal fun diagnoseAndroidBuildFailureDetailed(output: String, exitCode: Int): Diagnosis {
    val text = output
    val lower = output.lowercase()

    when {
        "no space left on device" in lower -> return Diagnosis(
            "Insufficient storage",
            "The device ran out of free storage during the build.",
            "Free space on the device, then retry.",
        )
        "outofmemoryerror" in lower || "java heap space" in lower || "metaspacesize" in lower -> return Diagnosis(
            "Out of memory",
            "Gradle or the Kotlin/Java compiler ran out of memory.",
            "Close other apps and retry the build.",
        )
        "permission denied" in lower -> return Diagnosis(
            "Permission denied",
            extractPrimaryErrorLine(text) ?: "A file or process permission error stopped the build.",
            "Check file permissions in the project and build directories.",
        )
    }

    parseDependencyResolutionIssues(output).firstOrNull()?.let {
        return Diagnosis(it.title, it.detail, it.suggestion)
    }
    parsePluginResolutionIssues(output).firstOrNull()?.let {
        return Diagnosis(it.title, it.detail, it.suggestion)
    }
    parseAndroidPipelineIssues(output).firstOrNull()?.let {
        return Diagnosis(it.title, it.detail, it.suggestion)
    }
    parseExecutionIssues(output).firstOrNull()?.let {
        return Diagnosis(it.title, it.detail, it.suggestion)
    }

    if ("compilation error" in lower || "compilation failed" in lower || "unresolved reference" in lower) {
        return Diagnosis(
            "Compilation failed",
            extractPrimaryErrorLine(text) ?: "Kotlin or Java compilation failed.",
            "Open the build log for the source file and line.",
        )
    }
    if ("minimum supported gradle version" in lower || "maximum supported gradle" in lower ||
        "this version of the android gradle plugin requires" in lower
    ) {
        return Diagnosis(
            "Gradle / AGP incompatibility",
            extractPrimaryErrorLine(text) ?: "Gradle and Android Gradle Plugin versions are incompatible.",
            "Align the Gradle wrapper and Android Gradle Plugin versions.",
        )
    }

    val primary = extractPrimaryErrorLine(text)
    return Diagnosis(
        "Build failed",
        primary ?: "Gradle build failed (exit code $exitCode).",
        "Open the full build log for the original Gradle output.",
    )
}

internal fun parseDependencyResolutionIssues(output: String): List<AndroidBuildIssue> {
    val lower = output.lowercase()
    val hasResolveSignal =
        "could not resolve" in lower ||
            "could not find" in lower ||
            "could not get" in lower ||
            "failed to resolve" in lower ||
            "could not download" in lower ||
            "received status code" in lower
    if (!hasResolveSignal) return emptyList()

    val issues = mutableListOf<AndroidBuildIssue>()
    val coordinates = extractAllArtifactCoordinates(output)
    val artifact = coordinates.firstOrNull()
    val searched = extractSearchedLocations(output)
    val http = extractHttpFailure(output)
    val network = extractNetworkException(output)
    val offline = "no cached version" in lower && ("--offline" in lower || "offline mode" in lower || "the offline mode" in lower)
    val localOnly = searched.isNotEmpty() && searched.all { it.startsWith("file:") || it.startsWith("/") || "localmvn" in it.lowercase() }
    val remoteAttempted = searched.any {
        it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
    }

    when {
        offline -> {
            issues += AndroidBuildIssue(
                title = "Offline mode — artifact not cached",
                detail = buildString {
                    if (artifact != null) append(artifact).append(" was not available offline.")
                    else append("A required artifact was not available in offline mode.")
                    if (searched.isNotEmpty()) append(" Searched: ").append(searched.take(3).joinToString("; "))
                },
                suggestion = "Disable offline mode or ensure the artifact is present in the local cache/mirror.",
            )
        }
        network != null && remoteAttempted -> {
            issues += AndroidBuildIssue(
                title = network.title,
                detail = buildString {
                    if (artifact != null) append(artifact).append(" — ")
                    append(network.detail)
                    if (searched.isNotEmpty()) append(" Locations: ").append(searched.take(3).joinToString("; "))
                },
                suggestion = network.suggestion,
            )
        }
        http != null -> {
            issues += AndroidBuildIssue(
                title = http.title,
                detail = buildString {
                    if (artifact != null) append(artifact).append(" — ")
                    append(http.detail)
                    http.url?.let { append(" (").append(it).append(")") }
                },
                suggestion = http.suggestion,
            )
        }
        localOnly -> {
            issues += AndroidBuildIssue(
                title = "Missing local artifact",
                detail = buildString {
                    if (coordinates.isNotEmpty()) {
                        append("Not found in the local Maven mirror: ")
                        append(coordinates.joinToString(", "))
                        append('.')
                    } else {
                        append("A dependency was not found in the local Maven mirror.")
                    }
                    if (searched.isNotEmpty()) {
                        append(" Searched local path(s): ")
                        append(searched.take(4).joinToString("; "))
                    }
                    append(" No remote repository failure appears in this log — only the local mirror was searched.")
                },
                suggestion = "Use Fix to download these artifacts into the local mirror, then Build again. Or ensure a remote repository is available to the build.",
                fixKind = if (coordinates.isNotEmpty()) AndroidBuildIssueFix.FETCH_LOCAL_MIRROR_ARTIFACTS else AndroidBuildIssueFix.NONE,
                coordinates = coordinates,
            )
        }
        artifact != null -> {
            val onlyLocalEvidence = searched.isNotEmpty() && searched.all {
                it.startsWith("file:") || it.startsWith("/") || "localmvn" in it.lowercase()
            }
            if (onlyLocalEvidence || searched.isEmpty()) {
                issues += AndroidBuildIssue(
                    title = "Missing local artifact",
                    detail = buildString {
                        append("Could not resolve ").append(coordinates.joinToString(", ")).append('.')
                        if (searched.isNotEmpty()) {
                            append(" Searched local path(s): ")
                            append(searched.take(4).joinToString("; "))
                        } else {
                            append(" No repository locations were listed in the log.")
                        }
                    },
                    suggestion = "Use Fix to fetch missing artifacts into the local Maven mirror, then Build again.",
                    fixKind = AndroidBuildIssueFix.FETCH_LOCAL_MIRROR_ARTIFACTS,
                    coordinates = coordinates,
                )
            } else {
                issues += AndroidBuildIssue(
                    title = "Dependency not resolved",
                    detail = buildString {
                        append("Could not resolve ").append(coordinates.joinToString(", ")).append('.')
                        append(" Searched: ")
                        append(searched.take(4).joinToString("; "))
                    },
                    suggestion = "Verify the coordinate/version and that a repository providing it is reachable. If the files only need to live in the local mirror, use Fix.",
                    fixKind = if (coordinates.isNotEmpty()) AndroidBuildIssueFix.FETCH_LOCAL_MIRROR_ARTIFACTS else AndroidBuildIssueFix.NONE,
                    coordinates = coordinates,
                )
            }
        }
        else -> {
            val line = extractPrimaryErrorLine(output)
            issues += AndroidBuildIssue(
                title = "Dependency resolution failed",
                detail = line ?: "Gradle could not resolve one or more dependencies.",
                suggestion = "Open the full build log for the artifact name, repositories searched, and any HTTP or network details.",
            )
        }
    }
    return issues
}

internal fun parsePluginResolutionIssues(output: String): List<AndroidBuildIssue> {
    val lower = output.lowercase()
    if ("plugin" !in lower) return emptyList()
    val pluginSignals =
        ("plugin" in lower && "was not found" in lower) ||
            "plugin request" in lower ||
            "could not find specified plugin" in lower ||
            "failed to apply plugin" in lower ||
            "invalid plugin request" in lower ||
            ("pluginmanagement" in lower && "could not resolve" in lower) ||
            ("plugin repositories" in lower && "not found" in lower)
    if (!pluginSignals) return emptyList()
    val pluginId = Regex("""[A-Za-z0-9._-]+:[A-Za-z0-9._-]+(?::[A-Za-z0-9._-]+)?""")
        .find(output)?.value
    return listOf(
        AndroidBuildIssue(
            title = "Plugin resolution failed",
            detail = buildString {
                if (pluginId != null) append("Plugin ").append(pluginId).append(" could not be resolved or applied. ")
                append(extractPrimaryErrorLine(output) ?: "Gradle failed while resolving or applying a plugin.")
            },
            suggestion = "Check the plugin id/version and pluginManagement repositories in settings.gradle(.kts).",
        ),
    )
}

internal fun parseAndroidPipelineIssues(output: String): List<AndroidBuildIssue> {
    val lower = output.lowercase()
    val issues = mutableListOf<AndroidBuildIssue>()
    when {
        "manifest merger failed" in lower -> issues += AndroidBuildIssue(
            title = "Manifest merger failed",
            detail = extractPrimaryErrorLine(output) ?: "Android manifest merge failed.",
            suggestion = "Resolve conflicting manifest entries (permissions, application attributes, or library manifests).",
        )
        "aapt2" in lower || "android resource linking failed" in lower || "resource linking failed" in lower -> issues += AndroidBuildIssue(
            title = "Resource processing failed",
            detail = extractPrimaryErrorLine(output) ?: "Android resources could not be compiled or linked.",
            suggestion = "Fix invalid resources or XML references shown in the build log.",
        )
        "r8:" in lower || "r8." in lower || "proguard" in lower && "error" in lower -> issues += AndroidBuildIssue(
            title = "R8 / ProGuard failed",
            detail = extractPrimaryErrorLine(output) ?: "Code shrinking or obfuscation failed.",
            suggestion = "Review keep rules and the R8/ProGuard section of the build log.",
        )
        "dex" in lower && ("error" in lower || "failed" in lower) && "index overflow" in lower -> issues += AndroidBuildIssue(
            title = "DEX limit exceeded",
            detail = extractPrimaryErrorLine(output) ?: "Multi-dex or method count limit was exceeded.",
            suggestion = "Enable multiDex or reduce dependencies.",
        )
        "duplicate class" in lower -> issues += AndroidBuildIssue(
            title = "Duplicate classes",
            detail = extractPrimaryErrorLine(output) ?: "The same class was found in more than one dependency.",
            suggestion = "Exclude the conflicting transitive dependency or align versions.",
        )
        "packagingoptions" in lower || "packaging" in lower && "failed" in lower -> issues += AndroidBuildIssue(
            title = "Packaging failed",
            detail = extractPrimaryErrorLine(output) ?: "APK/AAB packaging failed.",
            suggestion = "Review packaging options and native library conflicts in the log.",
        )
        "ksp" in lower && ("failed" in lower || "error" in lower) -> issues += AndroidBuildIssue(
            title = "KSP failed",
            detail = extractPrimaryErrorLine(output) ?: "Kotlin Symbol Processing failed.",
            suggestion = "Check KSP processor configuration and the reported source.",
        )
        "kapt" in lower && ("failed" in lower || "error" in lower) -> issues += AndroidBuildIssue(
            title = "KAPT failed",
            detail = extractPrimaryErrorLine(output) ?: "Annotation processing (kapt) failed.",
            suggestion = "Check annotation processor configuration and the reported error.",
        )
    }
    return issues
}

internal fun parseExecutionIssues(output: String): List<AndroidBuildIssue> {
    val lower = output.lowercase()
    return when {
        "unable to start the daemon" in lower || "gradle daemon" in lower && "died" in lower -> listOf(
            AndroidBuildIssue(
                title = "Gradle daemon failed",
                detail = extractPrimaryErrorLine(output) ?: "The Gradle daemon failed to start or exited unexpectedly.",
                suggestion = "Retry the build; if it persists, free memory and storage.",
            ),
        )
        "timeout" in lower && ("waiting for" in lower || "timed out" in lower) -> listOf(
            AndroidBuildIssue(
                title = "Build timed out",
                detail = extractPrimaryErrorLine(output) ?: "A build step timed out.",
                suggestion = "Retry when the device is less busy, or increase the step timeout if configurable.",
            ),
        )
        else -> emptyList()
    }
}

internal data class HttpFailure(val title: String, val detail: String, val suggestion: String, val url: String?)
internal data class NetworkFailure(val title: String, val detail: String, val suggestion: String)


internal fun extractArtifactCoordinate(output: String): String? =
    extractAllArtifactCoordinates(output).firstOrNull()

/** All group:artifact:version references tied to resolution failures in the log. */
internal fun extractAllArtifactCoordinates(output: String): List<String> {
    val found = LinkedHashSet<String>()
    Regex(
        """Could not find\s+[A-Za-z0-9._-]+\.(?:jar|aar|pom)\s*\(\s*([A-Za-z0-9._-]+:[A-Za-z0-9._-]+:[A-Za-z0-9._-]+)\s*\)""",
        RegexOption.IGNORE_CASE,
    ).findAll(output).forEach { found += it.groupValues[1] }
    Regex(
        """Could not (?:find|resolve)(?: all files for configuration| any matches for)?[^\n]*?([A-Za-z0-9._-]+:[A-Za-z0-9._-]+:[A-Za-z0-9._-]+)""",
        RegexOption.IGNORE_CASE,
    ).findAll(output).forEach { found += it.groupValues[1] }
    Regex(
        """Failed to resolve(?: to version)?[:\s]+([A-Za-z0-9._-]+:[A-Za-z0-9._-]+:[A-Za-z0-9._-]+)""",
        RegexOption.IGNORE_CASE,
    ).findAll(output).forEach { found += it.groupValues[1] }
    return found.toList()
}

/**
 * Repository / filesystem locations Gradle reported for resolution.
 * Ignores documentation and help URLs that appear in generic Gradle output.
 */
internal fun extractSearchedLocations(output: String): List<String> {
    val results = LinkedHashSet<String>()
    Regex("""Searched in (?:the following locations?:)?\s*((?:[^\n]+\n?)+)""", RegexOption.IGNORE_CASE)
        .findAll(output)
        .forEach { block ->
            block.groupValues.getOrNull(1)?.lineSequence()?.forEach { line ->
                val cleaned = line.trim().trimStart('-', '*', ' ')
                if (isRealSearchLocation(cleaned)) results += cleaned.take(300)
            }
        }
    Regex("""(?:Required by|from)\s+(file:[^\s]+)""", RegexOption.IGNORE_CASE).findAll(output).forEach {
        results += it.groupValues[1]
    }
    Regex("""(file:/[^\s\]]+)""").findAll(output).forEach {
        val loc = it.groupValues[1].trimEnd('.', ',', ')', ']')
        if (isRealSearchLocation(loc)) results += loc
    }
    Regex("""((?:https?://)[^\s\]'"]+)""").findAll(output).forEach {
        val u = it.groupValues[1].trimEnd('.', ',', ')', ']')
        if (isRealSearchLocation(u)) results += u
    }
    return results.toList()
}

internal fun isRealSearchLocation(raw: String): Boolean {
    val s = raw.trim()
    if (s.isEmpty()) return false
    if (s.startsWith("file:")) return true
    if (s.startsWith("/") && ("maven" in s || "m2" in s || "repository" in s || s.endsWith(".jar") || s.endsWith(".pom") || s.endsWith(".aar"))) {
        return true
    }
    if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) {
        return false
    }
    val lower = s.lowercase()
    if ("docs.gradle.org" in lower || "help.gradle.org" in lower || "guides.gradle.org" in lower) return false
    if ("developer.android.com/studio" in lower) return false
    return listOf(
        "maven", "google", "repo.", "repository", ".jar", ".pom", ".aar",
        "dl.google.com", "maven.google.com", "repo1.maven.org", "plugins.gradle.org",
        "jitpack", "central.sonatype",
    ).any { it in lower }
}


internal fun extractHttpFailure(output: String): HttpFailure? {
    val statusMatch = Regex(
        """(?:Received status code|status code)\s+(\d{3})(?:\s+from\s+(\S+))?""",
        RegexOption.IGNORE_CASE,
    ).find(output)
    val code = statusMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
    val url = statusMatch?.groupValues?.getOrNull(2)?.takeIf { it.isNotBlank() }
        ?: Regex("""Could not GET\s+'([^']+)'""", RegexOption.IGNORE_CASE).find(output)?.groupValues?.getOrNull(1)
    if (code == null && url == null && "could not get resource" !in output.lowercase()) return null
    return when (code) {
        401 -> HttpFailure("Repository authentication failed (HTTP 401)", "HTTP 401 Unauthorized: access to the repository was not authorized.", "Check repository credentials if this repository requires authentication.", url)
        403 -> HttpFailure("Repository access forbidden (HTTP 403)", "HTTP 403 Forbidden: the repository refused access to the requested resource.", "Confirm you are allowed to use this repository and that the URL is correct.", url)
        404 -> HttpFailure("Artifact not found at repository (HTTP 404)", "HTTP 404 Not Found: the resource does not exist at that repository URL.", "Verify the dependency coordinate/version and the repository URL.", url)
        429 -> HttpFailure("Repository rate limited (HTTP 429)", "Too many requests were sent to the repository.", "Wait and retry; avoid rapid repeated builds against the same host.", url)
        in 500..599 -> HttpFailure("Repository server error (HTTP $code)", "The remote repository returned a server error.", "Retry later; the failure is on the repository host, not proven to be a local configuration error.", url)
        null -> null
        else -> HttpFailure("HTTP $code from repository", "The repository returned HTTP $code.", "Inspect the repository URL and response in the full build log.", url)
    }
}

internal fun extractNetworkException(output: String): NetworkFailure? {
    val lower = output.lowercase()
    return when {
        "unknownhostexception" in lower || "unable to resolve host" in lower || "nodename nor servname" in lower ->
            NetworkFailure(
                "DNS resolution failed",
                extractPrimaryErrorLine(output) ?: "A host name could not be resolved (DNS).",
                "Check DNS and that the repository hostname is correct. This is not the same as a generic 'no internet' assumption without host evidence.",
            )
        "sockettimeoutexception" in lower || "connect timed out" in lower || "read timed out" in lower ->
            NetworkFailure(
                "Network timeout",
                extractPrimaryErrorLine(output) ?: "A connection or read timed out while contacting a repository.",
                "Retry on a more stable connection; the log shows a timeout, not necessarily a missing artifact.",
            )
        "sslhandshakeexception" in lower || "certificateexception" in lower || "pkix path" in lower ->
            NetworkFailure(
                "TLS / certificate error",
                extractPrimaryErrorLine(output) ?: "TLS handshake or certificate validation failed.",
                "Check device date/time and whether the repository uses a trusted certificate.",
            )
        "connectexception" in lower || "failed to connect" in lower || "network is unreachable" in lower ->
            NetworkFailure(
                "Connection failed",
                extractPrimaryErrorLine(output) ?: "Could not open a network connection to a repository host.",
                "Verify connectivity to that host; a connection failure is not the same as an artifact simply missing from a local mirror.",
            )
        else -> null
    }
}

internal fun extractPrimaryErrorLine(output: String): String? {
    val patterns = listOf(
        Regex("""(?m)^\s*(?:\* )?What went wrong:\s*\n\s*(.+)"""),
        Regex("""(?m)^> (Could not [^\n]+)"""),
        Regex("""(?m)^e: [^\n]+"""),
        Regex("""(?m)^FAILURE: [^\n]+"""),
        Regex("""(?m)^.*\berror:\s+(.+)$""", RegexOption.IGNORE_CASE),
    )
    for (p in patterns) {
        p.find(output)?.groupValues?.lastOrNull { it.isNotBlank() }?.trim()?.take(400)?.let { return it }
    }
    return output.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .lastOrNull { line ->
            val l = line.lowercase()
            "error" in l || "failed" in l || "could not" in l || "exception" in l
        }?.take(400)
}
