package com.dcon4.ttsebook.di

import android.content.Context
import com.dcon4.ttsebook.data.AppDatabase
import com.dcon4.ttsebook.data.BookDao
import com.dcon4.ttsebook.data.BookmarkDao
import com.dcon4.ttsebook.data.BookRepository
import com.dcon4.ttsebook.data.PositionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideBookDao(db: AppDatabase): BookDao = db.bookDao()

    @Provides
    fun providePositionDao(db: AppDatabase): PositionDao = db.positionDao()

    @Provides
    fun provideBookmarkDao(db: AppDatabase): BookmarkDao = db.bookmarkDao()
}
