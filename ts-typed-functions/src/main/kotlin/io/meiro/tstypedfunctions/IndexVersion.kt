package io.meiro.tstypedfunctions

/**
 * Shared version stamp for FileBasedIndex extensions in this plugin.
 *
 * Bump when [SignatureKey] canonicalization changes, when the set of
 * PSI nodes the indexers visit changes, or when accept/reject rules
 * change. SignatureStubIndex, ImplementationStubIndex, and FactoryStubIndex
 * all use this constant because they share canonicalization rules and
 * the module-scope filter in ModuleScope.kt.
 */
internal const val INDEX_VERSION: Int = 2
