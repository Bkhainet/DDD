package com.example.ddd

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MainActivity : AppCompatActivity() {

    // Объявление переменных компонентов UI
    private lateinit var currentWordTextView: TextView
    private lateinit var articleRadioGroup: RadioGroup
    private lateinit var resultTextView: TextView
    private lateinit var buttons: List<Button>
    private lateinit var progressBar: ProgressBar

    // Объявление переменных для обработки состояний
    private var handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable
    private var totalWordsCount = 0
    private var completedWordsCount = 0

    // Объявление помощника базы данных
    private lateinit var dbHelper: DatabaseHelper

    // Имена для ключей SharedPreferences
    companion object {
        private const val PREFS_NAME = "GermanLearningAppPrefs"
        private const val CURRENT_WORD_KEY = "CurrentWord"
        private const val COMPLETED_WORDS_COUNT_KEY = "CompletedWordsCount"
        private const val TRANSLATION_OPTIONS_KEY = "TranslationOptions"
        private const val SELECTED_ARTICLE_KEY = "SelectedArticle"
        private const val ARTICLES_VISIBILITY_KEY = "ArticlesVisibility"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Привязка компонентов UI к переменным
        currentWordTextView = findViewById(R.id.currentWordTextView)
        articleRadioGroup = findViewById(R.id.articleRadioGroup)
        resultTextView = findViewById(R.id.resultTextView)
        progressBar = findViewById(R.id.progressBar)

        // Привязка кнопок перевода к списку
        buttons = listOf(
            findViewById(R.id.translationOption1),
            findViewById(R.id.translationOption2),
            findViewById(R.id.translationOption3),
            findViewById(R.id.translationOption4)
        )

        // Инициализация помощника базы данных
        dbHelper = DatabaseHelper(this)

        // Проверка и заполнение базы данных, если она пуста
        if (dbHelper.isDatabaseEmpty()) {
            val words = dbHelper.readWordsFromJson(this)
            dbHelper.insertWordsIntoDatabase(this, words)
        }

        // Установка максимального значения для ProgressBar
        totalWordsCount = dbHelper.getCountOfLevelWords("A1")
        progressBar.max = totalWordsCount

        // Установка начального слова
        setRandomWord()
    }

    // Метод для установки случайного слова в TextView
    private fun setRandomWord() {
        val currentWord = dbHelper.getRandomWord("A1")
        currentWordTextView.text = currentWord.Word
        articleRadioGroup.clearCheck()
        currentWordTextView.setTextColor(ContextCompat.getColor(this, R.color.default_text_color))

        // Показ или скрытие RadioButton в зависимости от того, требуется ли артикль
        val isArticleNeeded = currentWord.Artikel!!.isNotBlank()
        findViewById<RadioButton>(R.id.derRadioButton).visibility = if (isArticleNeeded) View.VISIBLE else View.GONE
        findViewById<RadioButton>(R.id.dieRadioButton).visibility = if (isArticleNeeded) View.VISIBLE else View.GONE
        findViewById<RadioButton>(R.id.dasRadioButton).visibility = if (isArticleNeeded) View.VISIBLE else View.GONE

        // Установка вариантов перевода для кнопок
        showTranslationOptions(currentWord)
    }

    // Метод для отображения вариантов перевода в кнопках
    private fun showTranslationOptions(word: WordEntry) {
        val translations = dbHelper.getTranslationOptions(word, "A1")
        buttons.zip(translations).forEach { (button, translation) ->
            button.text = translation
            button.setOnClickListener { checkTranslation(it as Button, word) }
        }
    }

    // Метод проверки правильности выбранного перевода
    private fun checkTranslation(button: Button, word: WordEntry) {
        val userTranslation = button.text.toString()
        val selectedArticleId = articleRadioGroup.checkedRadioButtonId
        val selectedArticle = if (selectedArticleId != -1) findViewById<RadioButton>(selectedArticleId).text.toString() else ""

        // Проверка правильности ответа и обновление UI соответственно
        if (checkAnswer(selectedArticle, userTranslation, word)) {
            resultTextView.text = getString(R.string.correct)
            resultTextView.setTextColor(ContextCompat.getColor(this, R.color.correct_answer))
            currentWordTextView.setTextColor(ContextCompat.getColor(this, R.color.correct_answer))
        } else {
            resultTextView.text = getString(R.string.incorrect, word.Artikel, word.Word, word.Translation)
            resultTextView.setTextColor(ContextCompat.getColor(this, R.color.wrong_answer))
            currentWordTextView.setTextColor(ContextCompat.getColor(this, R.color.wrong_answer))
        }

        // Обновление UI после проверки ответа
        resultTextView.visibility = View.VISIBLE
        articleRadioGroup.visibility = View.GONE
        buttons.forEach { it.visibility = View.GONE }

        // Задержка перед показом следующего вопроса
        runnable = Runnable {
            setRandomWord()
            articleRadioGroup.visibility = View.VISIBLE
            buttons.forEach { it.visibility = View.VISIBLE }
            resultTextView.visibility = View.GONE
        }
        handler.postDelayed(runnable, 3000)

        // Обновление прогресса пользователя
        completedWordsCount++
        updateProgressBar()
    }

    // Метод для проверки правильности выбранного артикля и перевода
    private fun checkAnswer(selectedArticle: String, selectedTranslation: String, word: WordEntry): Boolean {
        return selectedArticle == word.Artikel && selectedTranslation == word.Translation
    }

    // Метод для обновления ProgressBar на основе прогресса пользователя
    private fun updateProgressBar() {
        progressBar.progress = completedWordsCount
    }

    // Метод для сериализации вариантов перевода в JSON строку
    private fun serializeTranslationOptions(options: List<String>): String {
        return Json.encodeToString(options)
    }

    // Метод для десериализации JSON строки обратно в список строк
    private fun deserializeTranslationOptions(json: String): List<String> {
        return Json.decodeFromString(json)
    }

    // Метод для сохранения состояния приложения в SharedPreferences
    override fun onPause() {
        super.onPause()
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        sharedPrefs.edit().apply {

            putString(CURRENT_WORD_KEY, currentWordTextView.text.toString())
            putInt(COMPLETED_WORDS_COUNT_KEY, completedWordsCount)
            val options = buttons.map { it.text.toString() }
            putString(TRANSLATION_OPTIONS_KEY, serializeTranslationOptions(options))
            putInt(SELECTED_ARTICLE_KEY, articleRadioGroup.checkedRadioButtonId)
            apply()

            // Сохранение информации о видимости артиклей
            val isArticleNeeded = currentWordTextView.text.toString().let { wordText ->
                val wordEntry = dbHelper.getWordByWordText(wordText)
                wordEntry?.Artikel?.isNotBlank() ?: false
            }
            putBoolean(ARTICLES_VISIBILITY_KEY, isArticleNeeded)
            apply()
        }
    }

    // Метод для восстановления состояния приложения из SharedPreferences
    override fun onResume() {
        super.onResume()
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val currentWord = sharedPrefs.getString(CURRENT_WORD_KEY, "")
        if (currentWord!!.isNotEmpty()) {
            currentWordTextView.text = currentWord
        }
        completedWordsCount = sharedPrefs.getInt(COMPLETED_WORDS_COUNT_KEY, 0)
        updateProgressBar()

        val jsonOptions = sharedPrefs.getString(TRANSLATION_OPTIONS_KEY, null)
        jsonOptions?.let {
            val options = deserializeTranslationOptions(it)
            if (options.isNotEmpty()) {
                buttons.zip(options).forEach { (button, option) ->
                    button.text = option
                    button.setOnClickListener {
                        // Получаем текущее слово из TextView, так как оно должно соответствовать кнопке
                        val currentWord = currentWordTextView.text.toString()
                        // Находим полное WordEntry, используя текст кнопки
                        val wordEntry = dbHelper.getWordByWordText(currentWord)
                        if (wordEntry != null) {
                            checkTranslation(it as Button, wordEntry)
                        } else {
                            // Обработка ситуации, когда WordEntry не найден
                        }
                    }
                }
            }
        }
        // Восстановление видимости артиклей
        val isArticleNeeded = sharedPrefs.getBoolean(ARTICLES_VISIBILITY_KEY, true)
        setArticlesVisibility(isArticleNeeded)
    }

    // Вызов этого метода обновит видимость радиокнопок на основе того, требуется ли артикль
    private fun setArticlesVisibility(isVisible: Boolean) {
        val visibility = if (isVisible) View.VISIBLE else View.GONE
        findViewById<RadioButton>(R.id.derRadioButton).visibility = visibility
        findViewById<RadioButton>(R.id.dieRadioButton).visibility = visibility
        findViewById<RadioButton>(R.id.dasRadioButton).visibility = visibility
    }

    // Метод для очистки отложенных задач при уничтожении Activity
    override fun onDestroy() {
        handler.removeCallbacks(runnable)
        super.onDestroy()
    }
}
