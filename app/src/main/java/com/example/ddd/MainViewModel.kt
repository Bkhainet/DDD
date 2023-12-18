package com.example.ddd

import android.annotation.SuppressLint
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext


class MainViewModel(private val dbHelper: DatabaseHelper) : ViewModel() {

    private val _currentWord = MutableLiveData<WordEntry?>()
    val currentWord: LiveData<WordEntry?> = _currentWord
    val wordsWithError: LiveData<List<WordEntry>> = dbHelper.wordsWithError

    private val _translationOptions = MutableLiveData<List<String>>()
    val translationOptions: LiveData<List<String>> = _translationOptions

    val errorWordsCount = MutableLiveData<Int>()

    // LiveData для прогресса
    private val _progressLiveData = MutableLiveData<Int>()
    val progressLiveData: LiveData<Int> = _progressLiveData

    @SuppressLint("Range")
    suspend fun getRandomWord(level: String) {
        Log.d("MainViewModel", "getRandomWord: Начинаем загрузку случайного слова для уровня $level")
        withContext(Dispatchers.IO) {
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
                    _currentWord.postValue(word)
                } else {
                    dbHelper.resetWordsUsage(level)
                    _currentWord.postValue(null)
                }
            }
        }
    }

    suspend fun getRandomWordWithError() {
        Log.d("MainViewModel", "getRandomWordWithError: Загрузка слова с ошибкой")

        withContext(Dispatchers.IO) {
            val wordWithError = dbHelper.getWordsWithError().randomOrNull()
            _currentWord.postValue(wordWithError)
        }
    }

    @SuppressLint("Range")
    suspend fun getTranslationOptions(word: WordEntry, level: String) {
        withContext(Dispatchers.IO) {
            Log.d("MainViewModel", "getTranslationOptions: Получение вариантов перевода для слова ${word.Word}")
            val db = dbHelper.readableDatabase
            val translations = mutableListOf<String>()
            val selectQuery = "SELECT ${DatabaseHelper.KEY_TRANSLATION} FROM ${DatabaseHelper.TABLE_WORDS} WHERE ${DatabaseHelper.KEY_WORD} != ? AND ${DatabaseHelper.KEY_LEVEL} = ? ORDER BY RANDOM() LIMIT 3"
            db.rawQuery(selectQuery, arrayOf(word.Word, level)).use { cursor ->
                while (cursor.moveToNext()) {
                    translations.add(cursor.getString(cursor.getColumnIndex(DatabaseHelper.KEY_TRANSLATION)))
                }
            }
            translations.add(word.Translation) // Добавляем верный перевод
            translations.shuffle()
            Log.d("MainViewModel", "Варианты перевода для '${word.Word}': $translations") // Логируем все варианты перевода
            _translationOptions.postValue(translations)
        }
    }

    private fun markWordAsUsed(word: WordEntry) {
        Log.d("MainViewModel", "markWordAsUsed: Помечаем слово '${word.Word}' как использованное")
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

    fun updateErrorWordsCount() {
        viewModelScope.launch {
            val count = dbHelper.getErrorWordsCount()
            errorWordsCount.postValue(count)
        }
    }

    fun updateProgress(progress: Int) {
        _progressLiveData.postValue(progress)
    }

    suspend fun loadWordByWordText(wordText: String) {
        Log.d("MainViewModel", "loadWordByWordText: Загрузка слова по тексту '$wordText'")

        withContext(Dispatchers.IO) {
            val word = dbHelper.getWordByWordText(wordText)
            Log.d("MainViewModel", "loadWordByWordText: Слово '$wordText' загружено")

            _currentWord.postValue(word)
        }
    }

    fun refreshWordsWithError() {
        dbHelper.updateWordsWithError()
    }
}