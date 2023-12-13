package com.example.ddd

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json


class DatabaseHelper(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), CoroutineScope {

    override val coroutineContext = Dispatchers.IO

    companion object {
        private const val DATABASE_VERSION = 4
        private const val DATABASE_NAME = "WordsDatabase"
        internal const val TABLE_WORDS = "Words"

        private const val KEY_ID = "id"
        internal const val KEY_ARTIKEL = "Artikel"
        internal const val KEY_WORD = "Word"
        internal const val KEY_TRANSLATION = "Translation"
        internal const val KEY_LEVEL = "Level"
        internal const val KEY_IS_USED = "isUsed"
    }

    override fun onCreate(db: SQLiteDatabase) {

        val createWordsTable = ("CREATE TABLE $TABLE_WORDS (" +
                "$KEY_ID INTEGER PRIMARY KEY," +
                "$KEY_ARTIKEL TEXT," +
                "$KEY_WORD TEXT," +
                "$KEY_TRANSLATION TEXT," +
                "$KEY_LEVEL TEXT," +
                "$KEY_IS_USED INTEGER DEFAULT 0)")
        db.execSQL(createWordsTable)

        launch {
            if (isDatabaseEmpty(db)) {
                val initialWords = readWordsFromJson()
                insertWordsIntoDatabase(db, initialWords)
            }
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_WORDS")
        onCreate(db)
    }

    private suspend fun isDatabaseEmpty(db: SQLiteDatabase): Boolean = withContext(Dispatchers.IO) {
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_WORDS", null)
        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        count == 0
    }

    private fun readWordsFromJson(): List<WordEntry> {
        // Чтение JSON файла
        val jsonString = context.assets.open("german_words_with_articles.json").bufferedReader().use { it.readText() }
        return Json.decodeFromString(jsonString)
    }

    @SuppressLint("Range")
    fun getWordByWordText(wordText: String): WordEntry? {
        val db = this.readableDatabase
        val cursor = db.query(TABLE_WORDS, null, "$KEY_WORD = ?", arrayOf(wordText), null, null, null)
        var wordEntry: WordEntry? = null

        if (cursor.moveToFirst()) {
            val artikel = cursor.getString(cursor.getColumnIndex(KEY_ARTIKEL))
            val word = cursor.getString(cursor.getColumnIndex(KEY_WORD))
            val translation = cursor.getString(cursor.getColumnIndex(KEY_TRANSLATION))
            val level = cursor.getString(cursor.getColumnIndex(KEY_LEVEL))
            wordEntry = WordEntry(artikel, word, translation, level)
            Log.d("DatabaseHelper", "Слово найдено: $word")
        } else {
            Log.d("DatabaseHelper", "Слово не найдено: $wordText")
        }

        cursor.close()
        return wordEntry
    }

    private suspend fun insertWordsIntoDatabase(db: SQLiteDatabase, words: List<WordEntry>) = withContext(Dispatchers.IO) {
        Log.d("DatabaseHelper", "Inserting words into database")
        db.beginTransaction()
        try {
            for (word in words) {
                val values = ContentValues().apply {
                    put(KEY_ARTIKEL, word.Artikel)
                    put(KEY_WORD, word.Word)
                    put(KEY_TRANSLATION, word.Translation)
                    put(KEY_LEVEL, word.Level)
                }
                db.insert(TABLE_WORDS, null, values)
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error inserting words into database", e)
        } finally {
            db.endTransaction()
            // db.close() // Это не нужно здесь
        }
    }
    suspend fun initializeDatabase() = withContext(Dispatchers.IO) {
        Log.d("DatabaseHelper", "Проверка наличия данных в БД")
        if (isDatabaseEmpty(this@DatabaseHelper.writableDatabase)) {
            Log.d("DatabaseHelper", "БД пуста, начинается инициализация")
            val initialWords = readWordsFromJson()
            insertWordsIntoDatabase(this@DatabaseHelper.writableDatabase, initialWords)
            Log.d("DatabaseHelper", "БД успешно инициализирована")
        } else {
            Log.d("DatabaseHelper", "БД уже инициализирована")
        }
    }
}
