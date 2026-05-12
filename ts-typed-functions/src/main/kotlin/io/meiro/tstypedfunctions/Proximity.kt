package io.meiro.tstypedfunctions

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile

/**
 * Lower-is-closer score for ordering candidate implementations relative to the
 * signature alias.
 *
 *  - Files inside libraries / `node_modules` (i.e. outside project content roots)
 *    all return [Int.MAX_VALUE] so they sink to the bottom regardless of
 *    where they sit in the filesystem. We deliberately don't try to order
 *    library matches against each other — "all deps in one bucket" is good
 *    enough.
 *
 *  - Project files are scored by the negative of the path-segment prefix
 *    length shared with the signature file. Same-directory matches share
 *    the most prefix and get the smallest (best) score; siblings come next;
 *    distant directories come last. The score is stable: ties leave the input
 *    order unchanged when used with [kotlin.collections.sortedBy].
 */
internal fun proximityScore(
    signatureFile: VirtualFile?,
    candidateFile: VirtualFile?,
    project: Project,
): Int {
    if (candidateFile == null) return Int.MAX_VALUE
    if (ProjectFileIndex.getInstance(project).isInLibrary(candidateFile)) return Int.MAX_VALUE
    if (signatureFile == null) return 0
    val sigSegments = signatureFile.path.split('/')
    val candSegments = candidateFile.path.split('/')
    val limit = minOf(sigSegments.size, candSegments.size)
    var common = 0
    while (common < limit && sigSegments[common] == candSegments[common]) common++
    return -common
}
