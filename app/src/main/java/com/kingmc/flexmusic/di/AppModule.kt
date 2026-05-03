package com.kingmc.flexmusic.di

import android.content.Context
import androidx.room.Room
import com.kingmc.flexmusic.data.local.FlexMusicDatabase
import com.kingmc.flexmusic.data.repository.MusicRepository
import com.kingmc.flexmusic.data.repository.MusicRepositoryImpl
import com.kingmc.flexmusic.player.Media3PlayerController
import com.kingmc.flexmusic.player.PlayerController
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FlexMusicDatabase {
        return Room.databaseBuilder(
            context,
            FlexMusicDatabase::class.java,
            "flex_music.db"
        ).fallbackToDestructiveMigration().build()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindsModule {

    @Binds
    abstract fun bindMusicRepository(impl: MusicRepositoryImpl): MusicRepository

    @Binds
    abstract fun bindPlayerController(impl: Media3PlayerController): PlayerController
}
