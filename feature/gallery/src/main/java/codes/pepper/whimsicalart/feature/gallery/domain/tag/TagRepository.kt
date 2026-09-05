package codes.pepper.whimsicalart.feature.gallery.domain.tag

import kotlinx.coroutines.flow.Flow

/**
 * Durable storage of per-photo scene tags, backed by Room. [Flow] exposures let
 * the gallery re-filter live as tags are added/removed.
 */
interface TagRepository {

    /** Persist [tag]; overwrites any previous tag for the same photo/tag pair. */
    suspend fun upsert(tag: PhotoTag)

    /** Remove a single tag mapping. */
    suspend fun remove(uri: android.net.Uri, tag: SceneTag)

    /** Replace all tags for [uri] with [tags] (keeps the store consistent). */
    suspend fun replace(uri: android.net.Uri, tags: List<PhotoTag>)

    /** Emits all distinct scene tags currently stored. */
    fun observeTags(): Flow<List<SceneTag>>

    /** Emits the tag mappings for one photo. */
    fun observeFor(uri: android.net.Uri): Flow<List<PhotoTag>>

    /** URIs carrying [tag]. */
    suspend fun urisFor(tag: SceneTag): List<String>

    /** Emits the total number of stored tag rows (for diagnostics). */
    fun observeCount(): Flow<Int>
}