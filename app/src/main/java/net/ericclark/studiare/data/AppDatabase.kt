package net.ericclark.studiare.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Deck::class, Card::class, TagDefinition::class, ActiveSession::class, DeckCollection::class, CollectionDeckCrossRef::class], version = 8, exportSchema = false)
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
                // Uses the JSON string representation that matches the Gson converter
                val defaultLinkage = "{\"syncCardAdditions\":true,\"syncCardDeletions\":false,\"linkCardData\":true,\"linkCardOrder\":true,\"linkFieldConfig\":true,\"linkMetadata\":true,\"linkScoring\":true}"
                db.execSQL("ALTER TABLE `decks` ADD COLUMN `linkageSettings` TEXT NOT NULL DEFAULT '$defaultLinkage'")
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
                    .addMigrations(MIGRATION_7_8)
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}