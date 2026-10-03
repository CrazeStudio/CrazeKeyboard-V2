package com.example.suggestions

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalDictionaryDao {
    @Query("SELECT * FROM personal_dictionary ORDER BY frequency DESC, lastUsed DESC LIMIT 100")
    fun getAllWords(): Flow<List<PersonalDictionaryEntity>>

    @Query("SELECT * FROM personal_dictionary ORDER BY frequency DESC, lastUsed DESC LIMIT :limit")
    suspend fun getTopWords(limit: Int = 200): List<PersonalDictionaryEntity>

    @Query("SELECT word FROM personal_dictionary WHERE word LIKE :prefix || '%' ORDER BY frequency DESC LIMIT 10")
    suspend fun getMatchingWords(prefix: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(wordEntity: PersonalDictionaryEntity)

    @Query("UPDATE personal_dictionary SET frequency = frequency + 1, lastUsed = :timestamp WHERE word = :word")
    suspend fun incrementFrequency(word: String, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(wordEntity: PersonalDictionaryEntity)

    @Query("DELETE FROM personal_dictionary")
    suspend fun clearDictionary()
}
