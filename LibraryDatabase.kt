package com.example.archiveplayer

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_media", indices = [Index("sourceId")])
data class SavedMedia(
    @PrimaryKey val uri: String,
    val title: String,
    val source: String,
    val sourceId: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = 0L
)

@Entity(tableName = "bookmarks", indices = [Index("mediaUri")])
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaUri: String,
    val title: String,
    val positionMs: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_items", indices = [Index("playlistId")])
data class PlaylistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: String,
    val mediaUri: String,
    val title: String,
    val sortOrder: Int
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM saved_media ORDER BY lastPlayedAt DESC, addedAt DESC")
    fun observeLibrary(): Flow<List<SavedMedia>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMedia(media: SavedMedia)

    @Query("DELETE FROM saved_media WHERE uri = :uri")
    suspend fun removeMedia(uri: String)

    @Insert
    suspend fun addBookmark(bookmark: Bookmark)

    @Query("SELECT * FROM bookmarks WHERE mediaUri = :uri ORDER BY positionMs")
    fun observeBookmarks(uri: String): Flow<List<Bookmark>>

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmark(bookmarkId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlaylist(playlist: Playlist)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<Playlist>>

    @Insert
    suspend fun addPlaylistItem(item: PlaylistItem)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY sortOrder")
    fun observePlaylistItems(playlistId: String): Flow<List<PlaylistItem>>

    @Query("DELETE FROM playlist_items WHERE id = :id")
    suspend fun deletePlaylistItem(id: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend deletePlaylist(id: String)
}

@Database(
    entities = [SavedMedia::class, Bookmark::class, Playlist::class, PlaylistItem::class],
    version = 1,
    exportSchema = false
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        @Volatile private var INSTANCE: LibraryDatabase? = null
        fun get(context: android.content.Context): LibraryDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LibraryDatabase::class.java,
                    "archive_player_library.db"
                ).build().also { INSTANCE = it }
            }
    }
}
