package net.ericclark.studiare.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Deck::class, Card::class, TagDefinition::class, ActiveSession::class, MediaAttachment::class], version = 6, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao
    abstract fun tagDao(): TagDao
    abstract fun sessionDao(): SessionDao
    abstract fun mediaAttachmentDao(): MediaAttachmentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_attachments` (
                        `id` TEXT NOT NULL,
                        `cardId` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `localFilename` TEXT NOT NULL,
                        `originalFilename` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`cardId`) REFERENCES `cards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_attachments_cardId` ON `media_attachments` (`cardId`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "studiare_database"
                )
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}