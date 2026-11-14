package com.jarves.mh.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidBuildIssuesDiagnosisTest {

    private val root = File("/tmp/mh-test-project").apply { mkdirs() }

    @Test
    fun localMissingArtifact_isNotClassifiedAsInternetFailure() {
        val log = """
            FAILURE: Build failed with an exception.
            * What went wrong:
            Execution failed for task ':app:compileDebugKotlin'.
            > Could not resolve all files for configuration ':app:debugCompileClasspath'.
               > Could not find error_prone_annotations-2.18.0.jar (com.google.errorprone:error_prone_annotations:2.18.0).
                 Searched in the following locations:
                     file:/root/maven/localMvnRepository/com/google/errorprone/error_prone_annotations/2.18.0/error_prone_annotations-2.18.0.jar
        """.trimIndent()
        val issues = parseAndroidBuildIssues(log, root, 1)
        val text = issues.joinToString("\n") { "${it.title}\n${it.detail}\n${it.suggestion}" }
        assertTrue(text.contains("Missing local", ignoreCase = true) || text.contains("local", ignoreCase = true))
        assertFalse(text.contains("Connect to the internet", ignoreCase = true))
        assertFalse(text.contains("internet connection", ignoreCase = true))
        assertFalse(text.contains("docs.gradle.org"))
        assertTrue(text.contains("error_prone_annotations") || text.contains("2.18.0") || text.contains("errorprone"))
        assertTrue(text.contains("file:/root/maven") || text.contains("localMvnRepository"))
        assertTrue(issues.any { it.fixKind == AndroidBuildIssueFix.FETCH_LOCAL_MIRROR_ARTIFACTS })
    }

    @Test
    fun docsGradleUrl_notTreatedAsRemoteRepoSearch() {
        val log = """
            Could not find error_prone_annotations-2.18.0.jar (com.google.errorprone:error_prone_annotations:2.18.0).
                 Searched in the following locations:
                     file:/root/maven/localMvnRepository/com/google/errorprone/error_prone_annotations/2.18.0/error_prone_annotations-2.18.0.jar
            See https://docs.gradle.org/8.14.3/userguide/gradle_daemon.html
            https://help.gradle.org
        """.trimIndent()
        val issues = parseAndroidBuildIssues(log, root, 1)
        val text = issues.joinToString("\n") { "${it.title}\n${it.detail}" }
        assertTrue(text.contains("Missing local", ignoreCase = true) || text.contains("local", ignoreCase = true))
        assertFalse(text.contains("docs.gradle.org"))
        assertFalse(text.contains("help.gradle.org"))
        assertTrue(issues.any { it.fixKind == AndroidBuildIssueFix.FETCH_LOCAL_MIRROR_ARTIFACTS })
        assertTrue(issues.any { "errorprone" in it.coordinates.joinToString() })
    }

    @Test
    fun dnsFailure_distinguishedFromHttp() {
        val log = """
            Could not resolve com.example:lib:1.0.
            > Could not get resource 'https://repo.example.com/com/example/lib/1.0/lib-1.0.pom'.
               > Could not GET 'https://repo.example.com/com/example/lib/1.0/lib-1.0.pom'.
                  > UnknownHostException: repo.example.com
        """.trimIndent()
        val diagnosis = diagnoseAndroidBuildFailure(log, 1)
        assertTrue(diagnosis.contains("DNS", ignoreCase = true) || diagnosis.contains("host", ignoreCase = true))
        assertFalse(diagnosis.contains("Connect to the internet or use an offline-cached version", ignoreCase = true))
    }

    @Test
    fun http404_classifiedAsNotFoundAtRepository() {
        val log = """
            Could not resolve com.example:lib:9.9.9.
            > Could not GET 'https://repo.maven.apache.org/maven2/com/example/lib/9.9.9/lib-9.9.9.pom'.
               Received status code 404 from server: Not Found
        """.trimIndent()
        val issues = parseAndroidBuildIssues(log, root, 1)
        val text = issues.joinToString(" ") { it.title + " " + it.detail }
        assertTrue(text.contains("404"))
        assertFalse(text.contains("Connect to the internet or use an offline-cached version", ignoreCase = true))
    }

    @Test
    fun http403_isForbiddenNotDisconnected() {
        val log = """
            Could not GET 'https://private.example.com/maven/lib.pom'.
            Received status code 403 from server: Forbidden
        """.trimIndent()
        val text = diagnoseAndroidBuildFailure(log, 1)
        assertTrue(text.contains("403") || text.contains("Forbidden", ignoreCase = true))
        assertFalse(text.contains("Connect to the internet or use an offline-cached version", ignoreCase = true))
    }

    @Test
    fun timeout_distinguishedFromDns() {
        val log = """
            Could not GET 'https://repo.example.com/lib.pom'.
            > SocketTimeoutException: Read timed out
        """.trimIndent()
        val text = diagnoseAndroidBuildFailure(log, 1)
        assertTrue(text.contains("timeout", ignoreCase = true))
        assertFalse(text.contains("DNS", ignoreCase = true))
    }

    @Test
    fun offlineMode_whenEvidencePresent() {
        val log = """
            Could not resolve com.example:lib:1.0.
            No cached version of com.example:lib:1.0 available for offline mode.
        """.trimIndent()
        val text = diagnoseAndroidBuildFailure(log, 1)
        assertTrue(text.contains("offline", ignoreCase = true))
    }

    @Test
    fun pluginFailure_notOrdinaryDependencyMessage() {
        val log = """
            Plugin [id: 'com.example.missing', version: '1.0'] was not found in any of the following sources:
            - Gradle Core Plugins
            - Plugin Repositories
        """.trimIndent()
        val issues = parseAndroidBuildIssues(log, root, 1)
        val text = issues.joinToString(" ") { it.title }
        assertTrue(text.contains("Plugin", ignoreCase = true))
    }

    @Test
    fun kotlinCompilation_detectedFromSourceLine() {
        val log = """
            e: /workspace/app/src/main/java/com/example/Main.kt:12:5 Unresolved reference: foo
        """.trimIndent()
        val issues = parseAndroidBuildIssues(log, root, 1)
        assertTrue(issues.any { it.title.contains("Unresolved", ignoreCase = true) || it.detail.contains("Unresolved") })
        assertTrue(issues.any { it.line == 12 })
    }

    @Test
    fun manifestMerger_distinct() {
        val log = "Manifest merger failed with multiple errors"
        val text = diagnoseAndroidBuildFailure(log, 1)
        assertTrue(text.contains("manifest", ignoreCase = true))
    }

    @Test
    fun unknownFailure_preservesOriginalRatherThanInventingInternet() {
        val log = "FAILURE: Build failed with an exception.\n* What went wrong:\nSome obscure internal Gradle error XYZ-42"
        val text = diagnoseAndroidBuildFailure(log, 7)
        assertFalse(text.contains("Connect to the internet or use an offline-cached version", ignoreCase = true))
        assertTrue(text.contains("XYZ-42") || text.contains("obscure") || text.contains("exit code 7"))
    }
}
