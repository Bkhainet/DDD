package com.example.ddd

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

// Основной класс Activity для приложения, который наследуется от AppCompatActivity.
class MainActivity : AppCompatActivity() {

    // Объявление переменных для хранения списка слов, текущего слова, UI элементов и Handler для задержки.
    private lateinit var wordsList: List<WordEntry>
    private lateinit var currentWord: WordEntry
    private lateinit var currentWordTextView: TextView
    private lateinit var articleRadioGroup: RadioGroup
    private lateinit var resultTextView: TextView
    private lateinit var buttons: List<Button>
    private var handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

    // Удаление всех callbacks для runnable, чтобы избежать утечек памяти при уничтожении Activity.
    override fun onDestroy() {
        handler.removeCallbacks(runnable)
        super.onDestroy()
    }

    // Метод onCreate вызывается при создании Activity.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Установка layout для Activity.

        // Инициализация UI компонентов.
        currentWordTextView = findViewById(R.id.currentWordTextView)
        articleRadioGroup = findViewById(R.id.articleRadioGroup)
        resultTextView = findViewById(R.id.resultTextView)

        // Список кнопок для вариантов перевода.
        buttons = listOf(
            findViewById(R.id.translationOption1),
            findViewById(R.id.translationOption2),
            findViewById(R.id.translationOption3),
            findViewById(R.id.translationOption4)
        )

        // Чтение JSON из assets и десериализация в список слов.
        val jsonString = assets.open("german_words_with_articles.json").bufferedReader().use { it.readText() }
        wordsList = Json.decodeFromString(jsonString)

        setRandomWord() // Установка случайного слова при запуске.
    }

    // Метод для установки случайного слова из списка.
    private fun setRandomWord() {
        currentWord = wordsList.random() // Выбор случайного слова.
        currentWordTextView.text = currentWord.Word // Отображение слова.
        articleRadioGroup.clearCheck() // Сброс выбора артикля.
        currentWordTextView.setTextColor(ContextCompat.getColor(this, R.color.default_text_color))

        val isArticleNeeded = !currentWord.Artikel.isNullOrBlank()

        // Установка видимости радиокнопок в зависимости от необходимости артикля.
        findViewById<RadioButton>(R.id.derRadioButton).visibility = if (isArticleNeeded) View.VISIBLE else View.GONE
        findViewById<RadioButton>(R.id.dieRadioButton).visibility = if (isArticleNeeded) View.VISIBLE else View.GONE
        findViewById<RadioButton>(R.id.dasRadioButton).visibility = if (isArticleNeeded) View.VISIBLE else View.GONE

        showTranslationOptions() // Показ вариантов перевода.
    }

    // Метод для отображения вариантов перевода.
    private fun showTranslationOptions() {
        // Получение списка переводов, исключая текущее слово, и добавление правильного перевода.
        val translations = (wordsList - currentWord).shuffled().take(3).map { it.Translation }.toMutableList()
        translations.add(currentWord.Translation)
        translations.shuffle() // Перемешивание списка переводов.

        // Установка текста на кнопки и обработчиков нажатий.
        buttons.zip(translations).forEach { (button, translation) ->
            button.text = translation
            button.setOnClickListener { checkTranslation(button) }
        }
    }

    // Метод для проверки выбранного перевода.
    private fun checkTranslation(button: Button) {
        val userTranslation = button.text.toString() // Получение перевода с кнопки.
        val selectedArticleId = articleRadioGroup.checkedRadioButtonId // Получение выбранного артикля.
        val selectedArticle = if (selectedArticleId != -1) findViewById<RadioButton>(selectedArticleId).text.toString() else ""

        // Проверка ответа и установка текста и цвета результата.
        if (checkAnswer(selectedArticle, userTranslation)) {
            resultTextView.text = "Правильно!"
            resultTextView.setTextColor(ContextCompat.getColor(this, R.color.correct_answer))
            currentWordTextView.text = "${currentWord.Artikel} ${currentWord.Word}"
            currentWordTextView.setTextColor(ContextCompat.getColor(this, R.color.correct_answer))
        } else {
            resultTextView.text = "Неправильно. Правильный ответ: ${currentWord.Artikel} ${currentWord.Word} - ${currentWord.Translation}"
            resultTextView.setTextColor(ContextCompat.getColor(this, R.color.wrong_answer))
            currentWordTextView.text = "${currentWord.Artikel} ${currentWord.Word}"
            currentWordTextView.setTextColor(ContextCompat.getColor(this, R.color.wrong_answer))
        }

        resultTextView.visibility = View.VISIBLE // Показ результата.
        articleRadioGroup.visibility = View.GONE
        buttons.forEach { it.visibility = View.GONE } // Скрытие кнопок.

        // Задержка перед показом следующего слова.
        runnable = Runnable {
            setRandomWord()
            articleRadioGroup.visibility = View.VISIBLE
            buttons.forEach { it.visibility = View.VISIBLE }
            resultTextView.visibility = View.GONE
            // Сброс цвета текста здесь может быть не нужен, если он уже сбрасывается в методе setRandomWord()
        }
        handler.postDelayed(runnable, 3000)

    }

    // Метод для проверки правильности ответа.
    private fun checkAnswer(selectedArticle: String, selectedTranslation: String): Boolean {
        val isArticleNeeded = !currentWord.Artikel.isNullOrBlank()
        val isArticleCorrect = !isArticleNeeded || selectedArticle == currentWord.Artikel
        val isTranslationCorrect = selectedTranslation == currentWord.Translation

        return isArticleCorrect && isTranslationCorrect
    }
}
