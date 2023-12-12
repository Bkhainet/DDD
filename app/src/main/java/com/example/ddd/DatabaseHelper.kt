package com.example.ddd

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json


class DatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 3
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
        if (isDatabaseEmpty(db)) { // Передаем объект базы данных в метод
            val initialWords = readWordsFromJson() // Используем сохраненный контекст
            insertWordsIntoDatabase(db, initialWords) // Передаем объект базы данных в метод
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_WORDS")
        onCreate(db)
    }


    private fun insertWordsIntoDatabase(db: SQLiteDatabase, words: List<WordEntry>) {
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
            //db.close()
        }
    }


    private fun isDatabaseEmpty(db: SQLiteDatabase): Boolean {
        // Измените определение метода, чтобы он принимал объект базы данных
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_WORDS", null)
        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        return count == 0
    }


    private fun readWordsFromJson(): List<WordEntry> {
        // Чтение и десериализация JSON из assets, используя сохранённый context
        val jsonString = context.assets.open("german_words_with_articles.json").bufferedReader().use { it.readText() }
        return Json.decodeFromString(jsonString)
    }

}
