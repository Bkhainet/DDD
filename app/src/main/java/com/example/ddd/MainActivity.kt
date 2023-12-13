package com.example.ddd

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.ddd.databinding.ActivityMainBinding
import kotlinx.coroutines.launch



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
    private var isDbInitialized = false


    companion object {
        private const val PREFS_NAME = "MyAppSettings"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_CURRENT_WORD = "current_word"
        private const val KEY_COMPLETED_COUNT = "completed_count"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Инициализация DBHelper и ViewModel
        dbHelper = DatabaseHelper(this)
        lifecycleScope.launch {
            dbHelper.initializeDatabase()
            isDbInitialized = true
            if (isDbInitialized) {
                restoreStateIfNeeded()
            }
        }

        val viewModelFactory = MainViewModelFactory(dbHelper)
        viewModel = ViewModelProvider(this, viewModelFactory).get(MainViewModel::class.java)

        checkFirstLaunchAndInitializeDb()
        setupUI()
    }

    private fun restoreStateIfNeeded() {
        if (!stateRestored) {
            val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val savedWordText = sharedPrefs.getString("${KEY_CURRENT_WORD}_$selectedLevel", null)
            completedWordsCount = sharedPrefs.getInt("${KEY_COMPLETED_COUNT}_$selectedLevel", 0)

            savedWordText?.let {
                lifecycleScope.launch {
                    currentWord = dbHelper.getWordByWordText(it)
                    currentWord?.let { word ->
                        updateUIWithCurrentWord(word)
                    } ?: setRandomWord(selectedLevel)
                }
            } ?: setRandomWord(selectedLevel)
            updateLevelAndProgressUI(selectedLevel, completedWordsCount)
            Log.d("MainActivity", "restoreStateIfNeeded: Восстановление состояния.")
            stateRestored = true
        }
    }


    private fun updateLevelAndProgressUI(level: String, progress: Int) {
        // Обновление ProgressBar с текущим прогрессом
        binding.progressBar.progress = progress

        // Дополнительно, если у вас есть элемент для отображения уровня, можно обновить его
        // Например, если у вас есть TextView для уровня:
        //binding.levelTextView.text = "Уровень: $level"
    }

    private fun checkFirstLaunchAndInitializeDb() {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (sharedPrefs.getBoolean(KEY_FIRST_LAUNCH, true)) {
            lifecycleScope.launch {
                dbHelper.initializeDatabase()
                sharedPrefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
                isDbInitialized = true
                restoreStateIfNeeded() // Вызов после инициализации БД
            }
        } else {
            isDbInitialized = true
        }
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
        if (currentWord == null) {
            Log.d("MainActivity", "setRandomWord: Текущее слово не установлено, загружаем новое слово.")
            lifecycleScope.launch {
                viewModel.getRandomWord(level)?.let { word ->
                    currentWord = word
                    updateUIWithCurrentWord(word)
                } ?: Log.d("MainActivity", "Слово для уровня $level не найдено")
            }
        } else {
            Log.d("MainActivity", "setRandomWord: Текущее слово уже установлено, пропускаем загрузку.")
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
        val isCorrect = word.Artikel?.let { artikel ->
            checkAnswer(artikel, userTranslation, word)
        } ?: false

        updateAnswerUI(isCorrect)

        if (isCorrect) {
            completedWordsCount++
            saveLevelProgress(selectedLevel, completedWordsCount)
            updateProgressUI()
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
        //saveLevelProgress(selectedLevel, completedWordsCount) //SharedPreferenc
        updateProgressBar()
    }

    private fun updateProgressBar() {
        binding.progressBar.progress = completedWordsCount
    }

    private fun prepareForNextWord() {
        binding.apply {
            // Скрываем и сбрасываем элементы UI
            resultTextView.visibility = View.VISIBLE
            articleRadioGroup.visibility = View.GONE
            translationOption1.visibility = View.GONE
            translationOption2.visibility = View.GONE
            translationOption3.visibility = View.GONE
            translationOption4.visibility = View.GONE
        }

        handler.postDelayed({
            currentWord = null // Сброс текущего слова перед выбором нового
            setRandomWord(selectedLevel)
            binding.apply {
                // Показываем элементы UI для нового слова
                articleRadioGroup.visibility = View.VISIBLE
                translationOption1.visibility = View.VISIBLE
                translationOption2.visibility = View.VISIBLE
                translationOption3.visibility = View.VISIBLE
                translationOption4.visibility = View.VISIBLE
                resultTextView.visibility = View.GONE
            }
        }, 3000) // Задержка в 3 секунды перед выбором нового слова
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }


    private fun handleIntent(intent: Intent) {
        intent.getStringExtra("SelectedLevel")?.let { newLevel ->
            selectedLevel = newLevel
        }
    }

    private fun saveLevelProgress(level: String, progress: Int) {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putInt("${KEY_COMPLETED_COUNT}_$level", progress)
            apply()
        }
        Log.d("MainActivity", "Прогресс сохранен для уровня $level: $progress")
    }

    private fun loadLevelProgress(level: String): Int {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return sharedPrefs.getInt("${KEY_COMPLETED_COUNT}_$level", 0)
    }

    private fun loadWordsForLevel(level: String) {
        lifecycleScope.launch {
            completedWordsCount = loadLevelProgress(level)
            totalWordsCount = viewModel.getCountOfLevelWords(level)
            updateProgressUI()
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
            putString("${KEY_CURRENT_WORD}_$selectedLevel", currentWord?.Word)
            putInt("${KEY_COMPLETED_COUNT}_$selectedLevel", completedWordsCount)
            apply()
        }
        Log.d("MainActivity", "Сохранено для уровня $selectedLevel: слово = ${currentWord?.Word}, прогресс = $completedWordsCount")
    }

    private fun updateProgressUI() {
        val progressText = "$completedWordsCount из $totalWordsCount слов"
        binding.progressText.text = progressText
        binding.progressBar.max = totalWordsCount
        binding.progressBar.progress = completedWordsCount
        Log.d("MainActivity", "UI обновлен: $progressText")
    }

    override fun onPause() {
        super.onPause()
        saveCurrentState() // Сохраняем состояние при приостановке активности
        Log.d("MainActivity", "Состояние сохранено")
    }

    override fun onResume() {
        super.onResume()
        // Обновляем состояние, только если это необходимо
        if (intent.hasExtra("SelectedLevel")) {
            selectedLevel = intent.getStringExtra("SelectedLevel") ?: "A1"
            loadWordsForLevel(selectedLevel)
        } else if (!stateRestored) {
            restoreStateIfNeeded()
            stateRestored = true
        }
    }

    override fun onDestroy() {
        if (::runnable.isInitialized) {
            handler.removeCallbacks(runnable)
        }
        super.onDestroy()
    }
}
