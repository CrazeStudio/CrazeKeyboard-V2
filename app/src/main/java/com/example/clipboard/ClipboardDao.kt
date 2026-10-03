package com.example.clipboard

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {
    @Query("SELECT * FROM clipboard_items ORDER BY isPinned DESC, timestamp DESC")
    fun getAllItems(): Flow<List<ClipboardItemEntity>>

    @Query("SELECT * FROM clipboard_items WHERE isPinned = 1 ORDER BY timestamp DESC")
    fun getPinnedItems(): Flow<List<ClipboardItemEntity>>

    @Query("SELECT * FROM clipboard_items WHERE isPinned = 0 ORDER BY timestamp DESC")
    fun getUnpinnedItems(): Flow<List<ClipboardItemEntity>>

    @Query("SELECT * FROM clipboard_items ORDER BY timestamp DESC LIMIT 1")
    fun getLatestItem(): Flow<ClipboardItemEntity?>

    @Query("SELECT * FROM clipboard_items WHERE text = :text LIMIT 1")
    suspend fun findByText(text: String): ClipboardItemEntity?

    @Query("SELECT COUNT(*) FROM clipboard_items WHERE isPinned = 0")
    suspend fun getUnpinnedCount(): Int

    @Query("SELECT COUNT(*) FROM clipboard_items WHERE isPinned = 1")
    suspend fun getPinnedCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ClipboardItemEntity): Long

    @Update
    suspend fun update(item: ClipboardItemEntity)

    @Delete
    suspend fun delete(item: ClipboardItemEntity)

    @Query("DELETE FROM clipboard_items WHERE isPinned = 0")
    suspend fun clearUnpinned()

    @Query("DELETE FROM clipboard_items")
    suspend fun clearAll()

    /**
     * Delete unpinned items exceeding the recent limit (default: 10 copies).
     * Pinned items (isPinned = 1) are always safely preserved.
     */
    @Query("DELETE FROM clipboard_items WHERE isPinned = 0 AND id NOT IN (SELECT id FROM clipboard_items WHERE isPinned = 0 ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun trimUnpinned(limit: Int = 10)

    /**
     * Atomically saves copied text, updating existing timestamp if duplicate,
     * and strictly trims unpinned clipboard history to max 10 copies.
     */
    @Transaction
    suspend fun saveClip(text: String, unpinnedLimit: Int = 10): Long {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return -1L

        val existing = findByText(trimmed)
        val id = if (existing != null) {
            update(existing.copy(timestamp = System.currentTimeMillis()))
            existing.id
        } else {
            insert(
                ClipboardItemEntity(
                    text = trimmed,
                    timestamp = System.currentTimeMillis(),
                    isPinned = false
                )
            )
        }
        trimUnpinned(unpinnedLimit)
        return id
    }

    @Query("SELECT * FROM clipboard_items WHERE text LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun search(query: String): Flow<List<ClipboardItemEntity>>
}
