package dev.hardik.aiguardian.di

import android.content.Context
import dev.hardik.aiguardian.stt.VoskSTTEngine
import dev.hardik.aiguardian.stt.AndroidSpeechRecognizerEngine
import dev.hardik.aiguardian.stt.TranscriptHub

import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import dev.hardik.aiguardian.data.local.AppDatabase
import dev.hardik.aiguardian.data.local.MedicineDao
import dev.hardik.aiguardian.data.local.BlocklistDao
import androidx.room.Room

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideVoskSTTEngine(@ApplicationContext context: Context, hub: TranscriptHub): VoskSTTEngine {
        return VoskSTTEngine(context, hub)
    }

    @Provides
    @Singleton
    fun provideAndroidSpeechRecognizerEngine(
        @ApplicationContext context: Context,
        hub: TranscriptHub
    ): AndroidSpeechRecognizerEngine {
        return AndroidSpeechRecognizerEngine(context, hub)
    }

    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(@ApplicationContext context: Context): FusedLocationProviderClient {
        return LocationServices.getFusedLocationProviderClient(context)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ai_guardian_db"
        ).build()
    }

    @Provides
    fun provideMedicineDao(db: AppDatabase): MedicineDao = db.medicineDao()

    @Provides
    fun provideBlocklistDao(db: AppDatabase): BlocklistDao = db.blocklistDao()

    @Provides
    fun provideScamDao(db: AppDatabase): dev.hardik.aiguardian.data.local.ScamDao = db.scamDao()
}



