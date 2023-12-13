package com.example.ddd

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.ddd.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var runnable: Runnable
    private var handler = Handler(Looper.getMainLooper())
    private var totalWordsCount = 0
    private var completedWordsCount = 0
    private var selectedLevel = "A1"
    private var currentWord: WordEntry? = null // Определение переменной для текущего слова
    private lateinit var dbHelper: DatabaseHelper
    private var stateRestored = false
    private var isStateRestored = false


    companion object {
        private const val PREFS_NAME = "MyAppSettings"
        private const val KEY_CURRENT_WORD = "current_word"
        private const val KEY_CURRENT_LEVEL = "current_level"
        private const val KEY_COMPLETED_COUNT = "completed_count"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dbHelper = DatabaseHelper(this)

        val viewModelFactory = MainViewModelFactory(dbHelper)
        viewModel = ViewModelProvider(this, viewModelFactory).get(MainViewModel::class.java)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        setupUI()
        handleIntent(intent) // Обработка Intent

        selectedLevel = intent.getStringExtra("SelectedLevel") ?: "A1"
        loadWordsForLevel(selectedLevel)
    }

    private fun setupUI() {
        binding.apply {
            listOf(translationOption1, translationOption2, translationOption3, translationOption4).forEach { button ->
                button.setOnClickListener { clickedButton ->
                    currentWord?.let { word ->
                        checkTranslation(clickedButton as Button, word)
                    }
                }
            }
        }
    }

    private fun setRandomWord(level: String) {
        if (currentWord == null) { // Добавлена проверка, чтобы избежать перезаписи восстановленного слова
            lifecycleScope.launch {
                viewModel.getRandomWord(level)?.let { word ->
                    currentWord = word
                    updateUIWithCurrentWord(word)
                }
            }
            Log.d("MainActivity", "Вызов setRandomWord")
        }
    }

    private fun updateUIWithCurrentWord(word: WordEntry) {
        // Убедитесь, что этот код запускается на главном потоке
        runOnUiThread {
            binding.apply {
                currentWordTextView.text = word.Word
                articleRadioGroup.clearCheck()
                currentWordTextView.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.default_text_color))

                val isArticleNeeded = word.Artikel?.isNotBlank() ?: false
                derRadioButton.visibility = if (isArticleNeeded) View.VISIBLE else View.GONE
                dieRadioButton.visibility = if (isArticleNeeded) View.VISIBLE else View.GONE
                dasRadioButton.visibility = if (isArticleNeeded) View.VISIBLE else View.GONE

                showTranslationOptions(word)
            }
        }
    }

    private fun showTranslationOptions(word: WordEntry) {
        lifecycleScope.launch {
            viewModel.getTranslationOptions(word, selectedLevel).also { translations ->
                binding.apply {
                    val buttons = listOf(translationOption1, translationOption2, translationOption3, translationOption4)
                    buttons.zip(translations).forEach { (button, translation) ->
                        button.text = translation
                    }
                }
            }
        }
    }


    private fun checkTranslation(clickedButton: Button, word: WordEntry) {
        val userTranslation = clickedButton.text.toString()
        val selectedArticleId = binding.articleRadioGroup.checkedRadioButtonId
        val selectedArticle = if (selectedArticleId != -1) findViewById<RadioButton>(selectedArticleId).text.toString() else ""

        val isCorrect = checkAnswer(selectedArticle, userTranslation, word) // Проверка правильности ответа
        updateAnswerUI(isCorrect) // Обновление UI в зависимости от правильности ответа

        if (isCorrect) {
            completedWordsCount++
            saveLevelProgress(selectedLevel, completedWordsCount) // Сохранение прогресса
            updateProgressBar()
        }
        prepareForNextWord()
    }

    private fun checkAnswer(selectedArticle: String, selectedTranslation: String, word: WordEntry): Boolean {
        return selectedArticle == word.Artikel && selectedTranslation == word.Translation
    }

    private fun updateAnswerUI(isCorrect: Boolean) {
        binding.apply {
            resultTextView.text = if (isCorrect) getString(R.string.correct) else getString(R.string.incorrect, currentWord?.Artikel, currentWord?.Word, currentWord?.Translation)
            resultTextView.setTextColor(ContextCompat.getColor(this@MainActivity, if (isCorrect) R.color.correct_answer else R.color.wrong_answer))
            currentWordTextView.setTextColor(ContextCompat.getColor(this@MainActivity, if (isCorrect) R.color.correct_answer else R.color.wrong_answer))
        }
        completedWordsCount++
        saveLevelProgress(selectedLevel, completedWordsCount) //SharedPreferenc
        updateProgressBar()
    }

    private fun updateProgressBar() {
        binding.progressBar.progress = completedWordsCount
    }

    private fun prepareForNextWord() {
        binding.apply {
            resultTextView.visibility = View.VISIBLE
            articleRadioGroup.visibility = View.GONE
            translationOption1.visibility = View.GONE
            translationOption2.visibility = View.GONE
            translationOption3.visibility = View.GONE
            translationOption4.visibility = View.GONE
        }

        handler.postDelayed({
            setRandomWord(selectedLevel)
            binding.apply {
                articleRadioGroup.visibility = View.VISIBLE
                translationOption1.visibility = View.VISIBLE
                translationOption2.visibility = View.VISIBLE
                translationOption3.visibility = View.VISIBLE
                translationOption4.visibility = View.VISIBLE
                resultTextView.visibility = View.GONE
            }
        }, 3000)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        loadWordsForLevel(selectedLevel)
    }

    private fun handleIntent(intent: Intent) {
        intent.getStringExtra("SelectedLevel")?.let { newLevel ->
            selectedLevel = newLevel
        }
    }

    //////////////////////////////////////////////////////////////////// SharedPreferences
    private fun saveLevelProgress(level: String, progress: Int) {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putInt("progress_$level", progress)
            apply()
        }
    }
    private fun loadLevelProgress(level: String): Int {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return sharedPrefs.getInt("progress_$level", 0)
    }
    private fun loadWordsForLevel(level: String) {
        lifecycleScope.launch {
            completedWordsCount = loadLevelProgress(level)
            totalWordsCount = viewModel.getCountOfLevelWords(level)
            binding.progressBar.max = totalWordsCount
            updateProgressBar()
            setRandomWord(level)
        }
    }
    override fun onBackPressed() {
        val intent = Intent(this, LevelSelectionActivity::class.java)
        startActivity(intent)
        finish() // Если вы хотите завершить MainActivity
    }

    // Метод для сохранения текущего состояния в SharedPreferences.
    private fun saveCurrentState() {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putString(KEY_CURRENT_WORD, currentWord?.Word)
            putString(KEY_CURRENT_LEVEL, selectedLevel)
            putInt(KEY_COMPLETED_COUNT, completedWordsCount)
            apply()
        }
        Log.d("MainActivity", "Сохранено: уровень = $selectedLevel, слово = ${currentWord?.Word}, прогресс = $completedWordsCount")
    }

    // Метод для восстановления сохраненного состояния.
    private fun restoreState() {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        selectedLevel = sharedPrefs.getString(KEY_CURRENT_LEVEL, "A1") ?: "A1"
        val savedWordText = sharedPrefs.getString(KEY_CURRENT_WORD, null)
        completedWordsCount = sharedPrefs.getInt(KEY_COMPLETED_COUNT, 0)

        if (savedWordText != null) {
            lifecycleScope.launch {
                currentWord = dbHelper.getWordByWordText(savedWordText)
                currentWord?.let {
                    updateUIWithCurrentWord(it)
                    Log.d("MainActivity", "Состояние успешно восстановлено: $it")
                }
            }
        } else {
            setRandomWord(selectedLevel)
        }

        Log.d("MainActivity", "Попытка восстановить состояние: уровень = $selectedLevel, сохраненное слово = $savedWordText, прогресс = $completedWordsCount")
    }

    override fun onResume() {
        super.onResume()
        // Если состояние уже восстановлено, не нужно загружать новое случайное слово
        if (!stateRestored) {
            restoreState()
        }
    }

    override fun onPause() {
        super.onPause()
        saveCurrentState() // Сохраняем состояние при приостановке активности
        stateRestored = false // Сбрасываем флаг восстановления состояния
    }

    override fun onDestroy() {
        if (::runnable.isInitialized) {
            handler.removeCallbacks(runnable)
        }
        super.onDestroy()
    }
}
