package codes.pepper.whimsicalart.feature.gallery.data.tag

import codes.pepper.whimsicalart.feature.gallery.domain.tag.PhotoTag
import codes.pepper.whimsicalart.feature.gallery.domain.tag.SceneTag
import codes.pepper.whimsicalart.feature.gallery.domain.tag.TagRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomTagRepository @Inject constructor(
    private val dao: PhotoTagDao
) : TagRepository {

    override suspend fun upsert(tag: PhotoTag) {
        dao.upsert(PhotoTagEntity.fromDomain(tag))
    }

    override suspend fun remove(uri: android.net.Uri, tag: SceneTag) {
        dao.delete(uri.toString(), tag.name)
    }

    override suspend fun replace(uri: android.net.Uri, tags: List<PhotoTag>) {
        val uriString = uri.toString()
        dao.deleteAllForUri(uriString)
        tags.forEach { dao.upsert(PhotoTagEntity.fromDomain(it)) }
    }

    override fun observeTags(): Flow<List<SceneTag>> =
        dao.observeDistinctTags().map { strings ->
            strings.mapNotNull { runCatching { SceneTag.valueOf(it) }.getOrNull() }
        }

    override fun observeFor(uri: android.net.Uri): Flow<List<PhotoTag>> =
        dao.observeForUri(uri.toString()).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun urisFor(tag: SceneTag): List<String> =
        dao.urisForTag(tag.name)

    override fun observeCount(): Flow<Int> = dao.observeCount()
}