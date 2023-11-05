package com.example.ddd

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "WordsDatabase"
        private const val TABLE_WORDS = "Words"

        private const val KEY_ID = "id"
        private const val KEY_ARTIKEL = "Artikel"
        private const val KEY_WORD = "Word"
        private const val KEY_TRANSLATION = "Translation"
        private const val KEY_LEVEL = "Level"
        private const val KEY_IS_USED = "isUsed"
        private const val CURRENT_WORD_ENTRY_KEY = "CurrentWordEntry"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createWordsTable = ("CREATE TABLE " + TABLE_WORDS + "("
                + KEY_ID + " INTEGER PRIMARY KEY,"
                + KEY_ARTIKEL + " TEXT,"
                + KEY_WORD + " TEXT,"
                + KEY_TRANSLATION + " TEXT,"
                + KEY_LEVEL + " TEXT,"
                + KEY_IS_USED + " INTEGER DEFAULT 0" + ")") // Добавьте это поле
        db.execSQL(createWordsTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_WORDS")
        onCreate(db)
    }

    fun addWord(wordEntry: WordEntry) {
        val db = this.writableDatabase

        val values = ContentValues()
        values.put(KEY_ARTIKEL, wordEntry.Artikel)
        values.put(KEY_WORD, wordEntry.Word)
        values.put(KEY_TRANSLATION, wordEntry.Translation)
        values.put(KEY_LEVEL, wordEntry.Level)

        db.insert(TABLE_WORDS, null, values)
        db.close()
    }

    fun readWordsFromJson(context: Context): List<WordEntry> {
        val jsonString = context.assets.open("german_words_with_articles.json").bufferedReader().use { it.readText() }
        return Json.decodeFromString(jsonString)
    }

    fun insertWordsIntoDatabase(words1: MainActivity, words: List<WordEntry>) {
        val db = this.writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues()
            for (word in words) {
                values.put(KEY_ARTIKEL, word.Artikel)
                values.put(KEY_WORD, word.Word)
                values.put(KEY_TRANSLATION, word.Translation)
                values.put(KEY_LEVEL, word.Level)
                db.insert(TABLE_WORDS, null, values)
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            // Обработка исключения
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    @SuppressLint("Range")
    fun getRandomWord(level: String): WordEntry {
        val db = this.readableDatabase
        val selectQuery = "SELECT * FROM $TABLE_WORDS WHERE $KEY_LEVEL = ? AND $KEY_IS_USED = 0 ORDER BY RANDOM() LIMIT 1"
        db.rawQuery(selectQuery, arrayOf(level)).use {
            if (it.moveToFirst()) {
                val word = WordEntry(
                    it.getString(it.getColumnIndex(KEY_ARTIKEL)),
                    it.getString(it.getColumnIndex(KEY_WORD)),
                    it.getString(it.getColumnIndex(KEY_TRANSLATION)),
                    it.getString(it.getColumnIndex(KEY_LEVEL))
                )
                markWordAsUsed(word) // Отметьте слово как использованное
                return word
            }
        }
        resetWordsUsage() // Если не найдено неиспользованных слов, сбросьте все статусы и попробуйте снова
        return getRandomWord(level)
    }

    private fun markWordAsUsed(word: WordEntry) {
        val db = this.writableDatabase
        val contentValues = ContentValues()
        contentValues.put(KEY_IS_USED, 1)
        db.update(TABLE_WORDS, contentValues, "$KEY_WORD = ?", arrayOf(word.Word))
        db.close()
    }

    private fun resetWordsUsage() {
        val db = this.writableDatabase
        val contentValues = ContentValues()
        contentValues.put(KEY_IS_USED, 0)
        db.update(TABLE_WORDS, contentValues, null, null)
        db.close()
    }

    @SuppressLint("Range")
    fun getTranslationOptions(excludeWord: WordEntry, level: String): List<String> {
        val db = this.readableDatabase
        val translations = mutableListOf<String>()
        val selectQuery = "SELECT $KEY_TRANSLATION FROM $TABLE_WORDS WHERE $KEY_WORD != ? AND $KEY_LEVEL = ? ORDER BY RANDOM() LIMIT 3"
        db.rawQuery(selectQuery, arrayOf(excludeWord.Word, level)).use {
            while (it.moveToNext()) {
                translations.add(it.getString(it.getColumnIndex(KEY_TRANSLATION)))
            }
        }
        translations.add(excludeWord.Translation)
        translations.shuffle()
        return translations
    }

    fun isDatabaseEmpty(): Boolean {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_WORDS", null)
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()
        return count == 0
    }

    fun getCountOfLevelWords(level: String): Int {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_WORDS WHERE $KEY_LEVEL = ?", arrayOf(level))
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()
        return count
    }

    fun getWordByWordText(wordText: String): WordEntry? {
        val db = this.readableDatabase
        val cursor = db.query(TABLE_WORDS, null, "$KEY_WORD = ?", arrayOf(wordText), null, null, null)

        // Проверяем, найдено ли слово в базе данных
        if (cursor.moveToFirst()) {
            val artikelIndex = cursor.getColumnIndex(KEY_ARTIKEL)
            val wordIndex = cursor.getColumnIndex(KEY_WORD)
            val translationIndex = cursor.getColumnIndex(KEY_TRANSLATION)
            val levelIndex = cursor.getColumnIndex(KEY_LEVEL)

            // Проверяем, что все индексы корректны
            if (artikelIndex != -1 && wordIndex != -1 && translationIndex != -1 && levelIndex != -1) {
                val wordEntry = WordEntry(
                    cursor.getString(artikelIndex),
                    cursor.getString(wordIndex),
                    cursor.getString(translationIndex),
                    cursor.getString(levelIndex)
                )
                cursor.close()
                return wordEntry
            } else {
                cursor.close()
                // Здесь можно бросить исключение или вернуть null, в зависимости от вашей бизнес-логики
                return null
            }
        } else {
            cursor.close()
            // Слово не найдено
            return null
        }
    }
}
