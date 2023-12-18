package com.example.ddd

import android.annotation.SuppressLint
import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainViewModel(private val dbHelper: DatabaseHelper) : ViewModel() {
    @SuppressLint("Range")

//    suspend fun getRandomWord(level: String): WordEntry? = withContext(Dispatchers.IO) {
//        val db = dbHelper.readableDatabase
//        val selectQuery = "SELECT * FROM ${DatabaseHelper.TABLE_WORDS} WHERE ${DatabaseHelper.KEY_LEVEL} = ? AND ${DatabaseHelper.KEY_IS_USED} = 0 ORDER BY RANDOM() LIMIT 1"
//        db.rawQuery(selectQuery, arrayOf(level)).use { cursor ->
//            if (cursor.moveToFirst()) {
//                val word = WordEntry(
//                    cursor.getString(cursor.getColumnIndex(DatabaseHelper.KEY_ARTIKEL)),
//                    cursor.getString(cursor.getColumnIndex(DatabaseHelper.KEY_WORD)),
//                    cursor.getString(cursor.getColumnIndex(DatabaseHelper.KEY_TRANSLATION)),
//                    cursor.getString(cursor.getColumnIndex(DatabaseHelper.KEY_LEVEL))
//                )
//                Log.d("MainViewModel", "Найдено случайное слово: ${word.Word}")
//                markWordAsUsed(word)
//                word
//            } else {
//                Log.d("MainViewModel", "Слово для уровня $level не найдено")
//                null // Возвращает null, если слова не найдены
//            }
//        }
//    }

    suspend fun getRandomWord(level: String): WordEntry? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val selectQuery = "SELECT * FROM ${DatabaseHelper.TABLE_WORDS} WHERE ${DatabaseHelper.KEY_LEVEL} = ? AND ${DatabaseHelper.KEY_IS_USED} = 0 ORDER BY RANDOM() LIMIT 1"
        db.rawQuery(selectQuery, arrayOf(level)).use { cursor ->
            if (cursor.moveToFirst()) {
                val word = WordEntry(
                    cursor.getString(cursor.getColumnIndex(DatabaseHelper.KEY_ARTIKEL)),
                    cursor.getString(cursor.getColumnIndex(DatabaseHelper.KEY_WORD)),
                    cursor.getString(cursor.getColumnIndex(DatabaseHelper.KEY_TRANSLATION)),
                    cursor.getString(cursor.getColumnIndex(DatabaseHelper.KEY_LEVEL))
                )
                Log.d("MainViewModel", "Найдено случайное слово: ${word.Word}")
                markWordAsUsed(word)
                return@withContext word
            } else {
                // Возвращаем null или реализуем логику сброса флага isUsed для всех слов этого уровня
                dbHelper.resetWordsUsage(level) // Сброс флага использования слов
                null
                //return@withContext null
            }
        }
    }

    @SuppressLint("Range")
    suspend fun getTranslationOptions(word: WordEntry, level: String): List<String> = withContext(Dispatchers.IO) {
        Log.d("MainViewModel", "Запрос вариантов перевода для слова: ${word.Word}")
        val db = dbHelper.readableDatabase
        val translations = mutableListOf<String>()
        val selectQuery = "SELECT ${DatabaseHelper.KEY_TRANSLATION} FROM ${DatabaseHelper.TABLE_WORDS} WHERE ${DatabaseHelper.KEY_WORD} != ? AND ${DatabaseHelper.KEY_LEVEL} = ? ORDER BY RANDOM() LIMIT 3"
        db.rawQuery(selectQuery, arrayOf(word.Word, level)).use { cursor ->
            while (cursor.moveToNext()) {
                translations.add(cursor.getString(cursor.getColumnIndex(DatabaseHelper.KEY_TRANSLATION)))
            }
        }
        translations.add(word.Translation)
        translations.shuffle()
        Log.d("MainViewModel", "Варианты перевода: $translations")
        translations
    }

//    @SuppressLint("Range")
//    suspend fun getWordByWordText(wordText: String): WordEntry? = withContext(Dispatchers.IO) {
//        val db = dbHelper.readableDatabase
//        val selectQuery = "SELECT * FROM ${DatabaseHelper.TABLE_WORDS} WHERE ${DatabaseHelper.KEY_WORD} = ?"
//        db.rawQuery(selectQuery, arrayOf(wordText)).use { cursor ->
//            if (cursor.moveToFirst()) {
//                return@withContext WordEntry(
//                    cursor.getString(cursor.getColumnIndex(DatabaseHelper.KEY_ARTIKEL)),
//                    cursor.getString(cursor.getColumnIndex(DatabaseHelper.KEY_WORD)),
//                    cursor.getString(cursor.getColumnIndex(DatabaseHelper.KEY_TRANSLATION)),
//                    cursor.getString(cursor.getColumnIndex(DatabaseHelper.KEY_LEVEL))
//                )
//            }
//            null // Возвращает null, если слово не найдено
//        }
//    }

    private fun markWordAsUsed(word: WordEntry) {
        val db = dbHelper.writableDatabase
        val contentValues = android.content.ContentValues()
        contentValues.put(DatabaseHelper.KEY_IS_USED, 1)

        try {
            val updatedRows = db.update(DatabaseHelper.TABLE_WORDS, contentValues, "${DatabaseHelper.KEY_WORD} = ?", arrayOf(word.Word))
            if (updatedRows > 0) {
                Log.d("MainViewModel", "Статус слова '${word.Word}' обновлен как использованный")
            } else {
                Log.d("MainViewModel", "Слово '${word.Word}' не найдено или уже помечено как использованное")
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Ошибка при обновлении статуса слова '${word.Word}'", e)
        }
    }
//    private fun markWordAsUsed(word: WordEntry) {
//    val db = dbHelper.writableDatabase
//    val contentValues = android.content.ContentValues()
//    contentValues.put(DatabaseHelper.KEY_IS_USED, 1)
//    val affectedRows = db.update(DatabaseHelper.TABLE_WORDS, contentValues, "${DatabaseHelper.KEY_WORD} = ? AND ${DatabaseHelper.KEY_IS_USED} = 0", arrayOf(word.Word))
//        try {
//            if (affectedRows > 0) {
//                Log.d("MainViewModel", "Слово '${word.Word}' обновлено как использованное")
//            }
//        } catch (e: Exception) {
//            Log.e("MainViewModel", "Ошибка при обновлении статуса слова '${word.Word}'", e)
//        }
//    }

    suspend fun getCountOfLevelWords(level: String): Int = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val query = "SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_WORDS} WHERE ${DatabaseHelper.KEY_LEVEL} = ?"
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
}
