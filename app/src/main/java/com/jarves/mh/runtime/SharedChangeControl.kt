package com.jarves.mh.runtime

import android.content.Context
import com.jarves.mh.model.AgentKind
import com.jarves.mh.model.ChangeItem
import com.jarves.mh.model.ProviderKind
import com.jarves.mh.model.providersForAgent
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Workspace change review shared by every coding-agent bridge.
 *
 * Bridges own only their CLI protocol and event parsing. Undo / Keep / pending
 * diffs are platform services so Claude, DeepSeek Harness, and Antigravity
 * behave identically in the Changes tab and do not each reimplement the store.
 */
class SharedChangeControl(
    val checkpoints: WorkspaceCheckpoints,
) {
    constructor(context: Context) : this(WorkspaceCheckpoints(context.filesDir, context))

    fun configureProjectRoot(projectId: String, rootPath: String) {
        checkpoints.configureProjectRoot(projectId, rootPath)
    }

    suspend fun undoLastChanges(projectId: String): Boolean = withContext(Dispatchers.IO) {
        val checkpoint = checkpoints.checkpointDir(projectId)
        val backup = File(checkpoint, "project")
        if (!backup.isDirectory || !File(checkpoint, "changes.json").isFile) return@withContext false
        val workspace = checkpoints.ensureWorkspace(projectId)
        val paths = checkpoints.readChangedPaths(projectId).filterNot(checkpoints::isInternalRuntimePath)
        if (paths.isEmpty()) return@withContext false
        paths.forEach { path -> restorePath(workspace, backup, path) }
        checkpoint.deleteRecursively()
        true
    }

    suspend fun acceptLastChanges(projectId: String) {
        withContext(Dispatchers.IO) {
            checkpoints.checkpointDir(projectId).deleteRecursively()
        }
    }

    suspend fun loadPendingChanges(projectId: String): List<ChangeItem> = withContext(Dispatchers.IO) {
        val workspace = checkpoints.ensureWorkspace(projectId)
        val paths = checkpoints.readChangedPaths(projectId).filterNot(checkpoints::isInternalRuntimePath)
        if (paths.isEmpty()) emptyList() else checkpoints.buildChangeDetails(projectId, workspace, paths)
    }

    suspend fun undoFileChange(projectId: String, path: String): Boolean = withContext(Dispatchers.IO) {
        if (checkpoints.isInternalRuntimePath(path) || path !in checkpoints.readChangedPaths(projectId)) {
            return@withContext false
        }
        val workspace = checkpoints.ensureWorkspace(projectId)
        val backup = File(checkpoints.checkpointDir(projectId), "project")
        restorePath(workspace, backup, path)
        checkpoints.removeChangedPath(projectId, path)
        true
    }

    suspend fun acceptFileChange(projectId: String, path: String): Boolean = withContext(Dispatchers.IO) {
        if (checkpoints.isInternalRuntimePath(path) || path !in checkpoints.readChangedPaths(projectId)) {
            return@withContext false
        }
        val workspace = checkpoints.ensureWorkspace(projectId)
        val backup = File(checkpoints.checkpointDir(projectId), "project")
        val current = checkpoints.safeWorkspaceFile(workspace, path)
        val baseline = checkpoints.safeWorkspaceFile(backup, path)
        if (current.isFile) {
            baseline.parentFile?.mkdirs()
            current.copyTo(baseline, overwrite = true)
        } else if (baseline.isFile) {
            baseline.delete()
        }
        checkpoints.removeChangedPath(projectId, path)
        true
    }

    private fun restorePath(workspace: File, backup: File, path: String) {
        val target = checkpoints.safeWorkspaceFile(workspace, path)
        val original = checkpoints.safeWorkspaceFile(backup, path)
        if (original.isFile) {
            target.parentFile?.mkdirs()
            original.copyTo(target, overwrite = true)
        } else if (target.isFile) {
            target.delete()
        }
    }
}

/**
 * Process-wide services shared by all agent runtimes.
 * Construct once in [com.jarves.mh.ui.MainViewModel] and inject into bridges.
 */
class AgentPlatform(context: Context) {
    val changeControl = SharedChangeControl(context)
    val checkpoints: WorkspaceCheckpoints get() = changeControl.checkpoints

    fun configureProjectRoot(projectId: String, rootPath: String) {
        changeControl.configureProjectRoot(projectId, rootPath)
    }

    /** Provider kinds supported by [agent] — single catalog entry point for UI and setup. */
    fun providersFor(agent: AgentKind): List<ProviderKind> = providersForAgent(agent)
}
