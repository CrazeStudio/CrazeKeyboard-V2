package com.example.suggestions

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "personal_dictionary")
data class PersonalDictionaryEntity(
    @PrimaryKey
    val word: String,
    val frequency: Int = 1,
    val lastUsed: Long = System.currentTimeMillis()
)
