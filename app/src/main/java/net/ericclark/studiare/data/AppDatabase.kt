package net.ericclark.studiare.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Deck::class, Card::class, TagDefinition::class, ActiveSession::class, DeckCollection::class, CollectionDeckCrossRef::class], version = 22, exportSchema = false)
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

        // Renamed SessionMode.QUIZ -> TYPING_SCORED (Learn/Practice/Quiz/Games/Smart reorg):
        // Typing's graded/hint-toggleable screen was named "Quiz" back when Typing was the only
        // other mode besides Flashcard; now that "Quiz" is also a tab name that applies to every
        // gradeable mode, keeping a mode literally called QUIZ is exactly the confusion this reorg
        // removes. The first statement is the real fix — Converters.fromSessionMode/toSessionMode
        // (Converters.kt) persist by `.name` via a plain `valueOf()` with no legacy-alias fallback,
        // so any already-saved `mode='QUIZ'` row would silently coerce to FLASHCARD on load once
        // the enum constant no longer exists. The second statement is defensive belt-and-suspenders
        // insurance for the same TYPING+isGraded->QUIZ remap bug class fixed elsewhere in this
        // reorg (tracing the save path shows graded Typing sessions are already saved as QUIZ, not
        // as TYPING+isGraded=1, so this is very likely a no-op — but cheap to guard anyway).
        val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("UPDATE sessions SET mode = 'TYPING_SCORED' WHERE mode = 'QUIZ'")
                db.execSQL("UPDATE sessions SET mode = 'TYPING_SCORED' WHERE mode = 'TYPING' AND isGraded = 1")
            }
        }

        // Audio mode options (playback speed, plays per side, auto-advance) are per session, like the
        // other mode options. Defaults match ActiveSession's, so existing sessions keep today's behavior.
        val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `audioPlaybackSpeed` REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `audioReplayCount` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `audioAutoAdvance` INTEGER NOT NULL DEFAULT 1")
            }
        }

        // Answer/next-card delays and grid density were added to sessions after version 15 had shipped
        // to devices, so they get their own version rather than changing 14→15.
        val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `audioAnswerDelaySeconds` REAL NOT NULL DEFAULT 2.0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `audioNextCardDelaySeconds` REAL NOT NULL DEFAULT 2.0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `gridDensity` INTEGER NOT NULL DEFAULT 2")
            }
        }

        // Freeform's show-both-sides and swipe-navigation options, per session.
        val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `freeformShowBothSides` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `freeformSwipeNavigation` INTEGER NOT NULL DEFAULT 1")
            }
        }

        // Typing modes' options (ignore formatting, auto-submit, autocorrect, length hint), per session.
        val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `typingIgnoreFormatting` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `typingAutoSubmit` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `typingDisableAutocorrect` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `typingShowLengthHint` INTEGER NOT NULL DEFAULT 1")
            }
        }

        // Flashcard options: auto-flip seconds, double-tap to flip, random first side. Per session.
        val MIGRATION_18_19 = object : androidx.room.migration.Migration(18, 19) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `flashcardAutoFlipSeconds` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `flashcardDoubleTapToFlip` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `flashcardRandomizeFirstSide` INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Per-session mode options for Picking, Multiple Choice, Matching, Speaking, Anagram, Crossword, Hangman,
        // Memory and Word Search, all in one migration. Existing rows keep today's behaviour via the defaults.
        val MIGRATION_19_20 = object : androidx.room.migration.Migration(19, 20) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `requireConfirmTap` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `sessions` SET `requireConfirmTap` = 1 WHERE `mode` = 'LIST'")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `autoAdvanceAfterCorrect` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `autoAdvanceDelaySeconds` REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `autoListen` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `speakingFrontSpeed` REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `speakingBackSpeed` REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `listResetPosition` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `listDimWrongGuesses` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `listRemoveGuessed` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `matchingHighlightStyle` TEXT NOT NULL DEFAULT 'FILL'")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `matchingWrongDelayMs` INTEGER NOT NULL DEFAULT 1000")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `matchingShowCorrectDialog` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `anagramFirstLetterHint` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `anagramUppercase` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `anagramColorVowels` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `crosswordHighlightWord` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `crosswordAutoAdvanceCell` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `crosswordCompactClues` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `crosswordFeedbackMode` TEXT NOT NULL DEFAULT 'LETTER'")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `hangmanMaxMistakes` INTEGER NOT NULL DEFAULT 7")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `hangmanRevealSpeedMs` INTEGER NOT NULL DEFAULT 300")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `hangmanHideVisual` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `memoryFlipAnimation` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `memoryGrayMatched` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `memoryPeekSeconds` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `memoryWrongPairMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `wordSearchHideFound` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `wordSearchHighlightColor` INTEGER NOT NULL DEFAULT -14498466")
            }
        }

        // Matching's "correct match stays highlighted for" option (per session, like the wrong-match delay).
        val MIGRATION_20_21 = object : androidx.room.migration.Migration(20, 21) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `matchingCorrectHighlightMs` INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Listen & Speak's sound-effect toggles: listening started, correct, incorrect. Per session.
        val MIGRATION_21_22 = object : androidx.room.migration.Migration(21, 22) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `listenStartSound` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `listenCorrectSound` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `listenIncorrectSound` INTEGER NOT NULL DEFAULT 1")
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
                    .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22)
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}