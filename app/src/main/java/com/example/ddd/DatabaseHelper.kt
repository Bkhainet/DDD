package com.example.ddd

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class DatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), CoroutineScope {

    override val coroutineContext = Dispatchers.IO

    private val _wordsWithError = MutableLiveData<List<WordEntry>>()
    val wordsWithError: LiveData<List<WordEntry>> = _wordsWithError

    companion object {
        private const val DATABASE_VERSION = 2
        private const val DATABASE_NAME = "WordsDatabase"
        internal const val TABLE_WORDS = "Words"

        private const val KEY_ID = "id"
        internal const val KEY_ARTIKEL = "Artikel"
        internal const val KEY_WORD = "Word"
        internal const val KEY_TRANSLATION = "Translation"
        internal const val KEY_LEVEL = "Level"
        internal const val KEY_IS_USED = "isUsed"
        internal const val KEY_ERROR_FLAG = "ErrorFlag"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createWordsTable = ("CREATE TABLE $TABLE_WORDS (" +
                "$KEY_ID INTEGER PRIMARY KEY," +
                "$KEY_ARTIKEL TEXT," +
                "$KEY_WORD TEXT," +
                "$KEY_TRANSLATION TEXT," +
                "$KEY_LEVEL TEXT," +
                "$KEY_IS_USED INTEGER DEFAULT 0," +
                "$KEY_ERROR_FLAG INTEGER DEFAULT 0)")
        db.execSQL(createWordsTable)

        launch {
            if (isDatabaseEmpty(db)) {
                val initialWords = readWordsFromJson()
                insertWordsIntoDatabase(db, initialWords)
            }
        }
    }

    fun initializeDatabaseAsync() = CoroutineScope(Dispatchers.IO).launch {
        initializeDatabase()
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_WORDS")
        onCreate(db)
    }

    private suspend fun isDatabaseEmpty(db: SQLiteDatabase): Boolean = withContext(coroutineContext) {
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_WORDS", null)
        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        count == 0
    }

    private fun readWordsFromJson(): List<WordEntry> {
        val jsonString = context.assets.open("german_words_with_articles.json").bufferedReader().use { it.readText() }
        return Json.decodeFromString(jsonString)
    }

    @SuppressLint("Range")
    fun getWordByWordText(wordText: String): WordEntry? {
        val db = this.readableDatabase
        val cursor = db.query(TABLE_WORDS, null, "$KEY_WORD = ?", arrayOf(wordText), null, null, null)
        var wordEntry: WordEntry? = null

        if (cursor.moveToFirst()) {
            val artikelIndex = cursor.getColumnIndex(KEY_ARTIKEL)
            val wordIndex = cursor.getColumnIndex(KEY_WORD)
            val translationIndex = cursor.getColumnIndex(KEY_TRANSLATION)
            val levelIndex = cursor.getColumnIndex(KEY_LEVEL)

            if (wordIndex != -1) {
                val artikel = if (artikelIndex != -1) cursor.getString(artikelIndex) else null
                val word = cursor.getString(wordIndex) ?: "" // Используйте пустую строку, если значение null
                val translation = if (translationIndex != -1) cursor.getString(translationIndex) else null
                val level = if (levelIndex != -1) cursor.getString(levelIndex) ?: "" else ""
                wordEntry = translation?.let { WordEntry(artikel, word, it, level) }
                Log.d("com.example.ddd.DatabaseHelper", "Слово найдено: $word")
            } else {
                Log.d("com.example.ddd.DatabaseHelper", "Слово не найдено: $wordText")
            }
        }
        cursor.close()
        return wordEntry
    }

    private suspend fun insertWordsIntoDatabase(db: SQLiteDatabase, words: List<WordEntry>) = withContext(coroutineContext) {
        db.beginTransaction()
        try {
            val existingWordsQuery = "SELECT COUNT(*) FROM $TABLE_WORDS WHERE $KEY_WORD = ?"
            for (word in words) {
                val cursor = db.rawQuery(existingWordsQuery, arrayOf(word.Word))
                val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
                cursor.close()

                if (count == 0) {
                    val values = ContentValues().apply {
                        put(KEY_ARTIKEL, word.Artikel)
                        put(KEY_WORD, word.Word)
                        put(KEY_TRANSLATION, word.Translation)
                        put(KEY_LEVEL, word.Level)
                    }
                    db.insert(TABLE_WORDS, null, values)
                }
            }
            db.setTransactionSuccessful()
            Log.d("com.example.ddd.DatabaseHelper", "insertWordsIntoDatabase: Слова успешно вставлены в базу данных")
        } catch (e: Exception) {
            Log.e("com.example.ddd.DatabaseHelper", "insertWordsIntoDatabase: Ошибка при вставке слов в базу данных", e)
        } finally {
            db.endTransaction()
        }
    }

    suspend fun initializeDatabase() = withContext(coroutineContext) {
        if (isDatabaseEmpty(this@DatabaseHelper.writableDatabase)) {
            val initialWords = readWordsFromJson()
            insertWordsIntoDatabase(this@DatabaseHelper.writableDatabase, initialWords)
            Log.d("com.example.ddd.DatabaseHelper", "initializeDatabase: База данных успешно инициализирована")
        } else {
            Log.d("com.example.ddd.DatabaseHelper", "initializeDatabase: База данных уже инициализирована")
        }
    }

    fun resetWordsUsage(level: String) {
        val db = this.writableDatabase
        val contentValues = ContentValues()
        contentValues.put(KEY_IS_USED, 0)
        db.update(TABLE_WORDS, contentValues, "$KEY_LEVEL = ?", arrayOf(level))
    }

    fun updateErrorFlag(wordText: String, errorFlag: Int) {
        val db = this.writableDatabase
        val contentValues = ContentValues()
        contentValues.put(KEY_ERROR_FLAG, errorFlag)
        db.update(TABLE_WORDS, contentValues, "$KEY_WORD = ?", arrayOf(wordText))
    }

    fun getWordsWithError(): List<WordEntry> {
        val db = this.readableDatabase
        val cursor = db.query(TABLE_WORDS, null, "$KEY_ERROR_FLAG = 1", null, null, null, null)
        val words = mutableListOf<WordEntry>()

        if (cursor.moveToFirst()) {
            do {
                val artikelIndex = cursor.getColumnIndex(KEY_ARTIKEL)
                val wordIndex = cursor.getColumnIndex(KEY_WORD)
                val translationIndex = cursor.getColumnIndex(KEY_TRANSLATION)
                val levelIndex = cursor.getColumnIndex(KEY_LEVEL)
                val errorFlagIndex = cursor.getColumnIndex(KEY_ERROR_FLAG)

                val artikel = if (artikelIndex != -1) cursor.getString(artikelIndex) ?: "" else ""
                val word = if (wordIndex != -1) cursor.getString(wordIndex) ?: "" else ""
                val translation = if (translationIndex != -1) cursor.getString(translationIndex) ?: "" else ""
                val level = if (levelIndex != -1) cursor.getString(levelIndex) ?: "" else ""
                val errorFlag = if (errorFlagIndex != -1) cursor.getInt(errorFlagIndex) else 0

                words.add(WordEntry(artikel, word, translation, level, errorFlag))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return words
    }

    suspend fun getCompletedWordsCount(level: String): Int = withContext(Dispatchers.IO) {
        val db = this@DatabaseHelper.readableDatabase
        db.rawQuery("SELECT COUNT(*) FROM $TABLE_WORDS WHERE $KEY_LEVEL = ? AND $KEY_IS_USED = 1", arrayOf(level)).use { cursor ->
            if (cursor.moveToFirst()) {
                return@withContext cursor.getInt(0)
            }
            return@withContext 0
        }
    }

    suspend fun getTotalWordsCount(level: String): Int = withContext(Dispatchers.IO) {
        val db = this@DatabaseHelper.readableDatabase
        db.rawQuery("SELECT COUNT(*) FROM $TABLE_WORDS WHERE $KEY_LEVEL = ?", arrayOf(level)).use { cursor ->
            if (cursor.moveToFirst()) {
                return@withContext cursor.getInt(0)
            }
            return@withContext 0
        }
    }

    suspend fun getErrorWordsCount(): Int = withContext(Dispatchers.IO) {
        val db = this@DatabaseHelper.readableDatabase
        db.rawQuery("SELECT COUNT(*) FROM $TABLE_WORDS WHERE $KEY_ERROR_FLAG = 1", null).use { cursor ->
            if (cursor.moveToFirst()) {
                return@withContext cursor.getInt(0)
            }
            return@withContext 0
        }

    }

    suspend fun getCountOfLevelWords(level: String): Int = withContext(Dispatchers.IO) {
        val db = this@DatabaseHelper.readableDatabase
        val query = "SELECT COUNT(*) FROM ${TABLE_WORDS} WHERE ${KEY_LEVEL} = ?"
        val cursor = db.rawQuery(query, arrayOf(level))
        if (cursor.moveToFirst()) {
            val count = cursor.getInt(0)
            cursor.close()
            return@withContext count
        } else {
            cursor.close()
            return@withContext 0
        }
    }

    fun updateWordsWithError() {
        CoroutineScope(Dispatchers.IO).launch {
            val words = getWordsWithError()
            _wordsWithError.postValue(words)
        }
    }

}