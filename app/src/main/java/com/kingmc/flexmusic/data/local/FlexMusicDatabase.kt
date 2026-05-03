package com.kingmc.flexmusic.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SongEntity::class],
    version = 4,
    exportSchema = false
)
abstract class FlexMusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
}
