package net.ericclark.studiare.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Deck::class, Card::class, TagDefinition::class, ActiveSession::class, DeckCollection::class, CollectionDeckCrossRef::class], version = 13, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao
    abstract fun tagDao(): TagDao
    abstract fun sessionDao(): SessionDao
    abstract fun deckCollectionDao(): DeckCollectionDao

    companion object {

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. Create the new DeckCollection table
                db.execSQL("""
                CREATE TABLE IF NOT EXISTS `collections` (
                    `id` TEXT NOT NULL, 
                    `name` TEXT NOT NULL, 
                    `description` TEXT NOT NULL, 
                    `createdAt` INTEGER NOT NULL, 
                    `updatedAt` INTEGER NOT NULL, 
                    `isPendingSync` INTEGER NOT NULL, 
                    `isDeleted` INTEGER NOT NULL, 
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())

                // 2. Create the Cross-Reference table
                db.execSQL("""
                CREATE TABLE IF NOT EXISTS `collection_deck_cross_ref` (
                    `collectionId` TEXT NOT NULL, 
                    `deckId` TEXT NOT NULL, 
                    PRIMARY KEY(`collectionId`, `deckId`)
                )
            """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_deck_cross_ref_deckId` ON `collection_deck_cross_ref` (`deckId`)")

                // 3. Add the linkageSettings column to the decks table with the new default values
                val defaultLinkage = "{\"syncCardAdditions\":true,\"syncCardDeletions\":false,\"linkCardData\":true,\"linkCardOrder\":true,\"linkFieldConfig\":true,\"linkMetadata\":true,\"linkScoring\":true}"
                db.execSQL("ALTER TABLE `decks` ADD COLUMN `linkageSettings` TEXT NOT NULL DEFAULT '$defaultLinkage'")
            }
        }

        // --- NEW: Add the metadata columns to the cards table ---
        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // reviewLogs is a serialized JSON List, so the default empty value is '[]'
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `reviewLogs` TEXT NOT NULL DEFAULT '[]'")
                // absoluteDueDate is nullable, so it defaults to NULL
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `absoluteDueDate` INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `wordSearchWords` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `wordSearchGrid` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `wordSearchGridWidth` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `wordSearchGridHeight` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `wordSearchFoundWordIds` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        // The audio-mode split (see the Studiare roadmap plan): graded Audio sessions used
        // speech-to-text to check a spoken answer, which is now Listen & Speak's job — Audio
        // itself dropped that entirely. Remap forward so an existing graded session still opens
        // somewhere real instead of a mode whose graded behavior no longer exists. Ungraded
        // Audio sessions are untouched; that's still plain Audio. Deliberately held until this
        // migration (not added alongside the SessionMode enum values) since applying it earlier
        // — before `listenSpeakStudy` had a registered screen — would have silently stranded any
        // existing graded Audio session on an unreachable route.
        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("UPDATE sessions SET mode = 'LISTEN_SPEAK' WHERE mode = 'AUDIO' AND isGraded = 1")
            }
        }

        // Collapsed the 4 audio-overhaul modes (SPEECH_TO_TEXT/TEXT_TO_SPEECH practice,
        // LISTEN_SPEAK/LISTEN_TYPE quiz) into 2 (TYPED_LISTEN, SPOKEN_LISTEN) — practice vs quiz
        // is entirely a function of the already-stored `isGraded` column, so the 4-way split was
        // pure duplication (see `SessionMode.asString(isGraded)` in Values.kt). Lossless: isGraded
        // itself is untouched, so a session's practice/quiz behavior is unchanged after this.
        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("UPDATE sessions SET mode = 'TYPED_LISTEN' WHERE mode IN ('SPEECH_TO_TEXT', 'LISTEN_TYPE')")
                db.execSQL("UPDATE sessions SET mode = 'SPOKEN_LISTEN' WHERE mode IN ('TEXT_TO_SPEECH', 'LISTEN_SPEAK')")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "studiare_database"
                )
                    .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}