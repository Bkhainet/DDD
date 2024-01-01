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
    private var isWordLoading = false



    companion object {
        private const val PREFS_NAME = "MyAppSettings"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_CURRENT_WORD = "current_word"
        private const val KEY_COMPLETED_COUNT = "completed_count"

        fun getPrefsName(): String = PREFS_NAME
        fun getKeyCompletedCount(): String = KEY_COMPLETED_COUNT
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
        if (isFirstLaunch()) {
            lifecycleScope.launch {
                updateProgress() // Вызов после инициализации БД
            }
        } else {
            updateProgress() // Обновление прогресса если БД уже инициализирована
        }
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
                    updateProgress() // Обновление прогресса после восстановления состояния
                }
            } ?: setRandomWord(selectedLevel)
            updateLevelAndProgressUI(selectedLevel, completedWordsCount)
            Log.d("MainActivity", "restoreStateIfNeeded: Восстановление состояния для уровня $selectedLevel: слово = ${currentWord?.Word}, прогресс = $completedWordsCount")
            stateRestored = true
        }
    }

    private fun updateLevelAndProgressUI(level: String, progress: Int) {
        // Обновление ProgressBar с текущим прогрессом
        binding.progressBar.progress = progress
        //binding.levelTextView.text = "Уровень: $level"
    }

    private fun checkFirstLaunchAndInitializeDb() {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (sharedPrefs.getBoolean(KEY_FIRST_LAUNCH, true)) {
            lifecycleScope.launch {
                dbHelper.initializeDatabaseAsync().join() // Дождаться завершения инициализации
                sharedPrefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
                isDbInitialized = true
                updateProgress()
            }
        } else {
            isDbInitialized = true
            updateProgress()
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
        if (currentWord == null && !isWordLoading) {
            Log.d("MainActivity", "setRandomWord: Загружаем новое слово.")
            isWordLoading = true
            lifecycleScope.launch {
                viewModel.getRandomWord(level)?.let { word ->
                    currentWord = word
                    updateUIWithCurrentWord(word)
                } ?: run {
                    dbHelper.resetWordsUsage(level)
                    viewModel.getRandomWord(level)?.let { newWord ->
                        currentWord = newWord
                        updateUIWithCurrentWord(newWord)
                    }
                }
                isWordLoading = false
            }
        } else {
            Log.d("MainActivity", if (currentWord != null) "setRandomWord: Текущее слово уже установлено." else "setRandomWord: Загрузка слова уже идёт.")
        }
    }

    private fun updateUIWithCurrentWord(word: WordEntry) {
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

//    private fun checkTranslation(clickedButton: Button, word: WordEntry) {
//        val userTranslation = clickedButton.text.toString()
//        val isCorrect = word.Artikel?.let { artikel ->
//            checkAnswer(artikel, userTranslation, word)
//        } ?: false
//
//        updateAnswerUI(isCorrect)
//
//        if (isCorrect) {
//            //completedWordsCount++
//            saveLevelProgress(selectedLevel, completedWordsCount)
//            updateProgressUI()
//        }
//        prepareForNextWord()
//    }
    private fun checkTranslation(clickedButton: Button, word: WordEntry) {
        val selectedArticle = when (binding.articleRadioGroup.checkedRadioButtonId) {
            R.id.derRadioButton -> "der"
            R.id.dieRadioButton -> "die"
            R.id.dasRadioButton -> "das"
            else -> ""
        }
        val userTranslation = clickedButton.text.toString()
        val isCorrect = checkAnswer(selectedArticle, userTranslation, word)

        updateAnswerUI(isCorrect)

        if (isCorrect) {
            completedWordsCount++
            saveLevelProgress(selectedLevel, completedWordsCount)
            updateProgressUI()
        }
        prepareForNextWord()
    }

//    private fun checkAnswer(selectedArticle: String, selectedTranslation: String, word: WordEntry): Boolean {
//        return selectedArticle == word.Artikel && selectedTranslation == word.Translation
//    }

    private fun checkAnswer(selectedArticle: String, selectedTranslation: String, word: WordEntry): Boolean {
        // Проверяем, имеется ли артикль у слова
        val correctArticle = word.Artikel ?: ""
        // Проверяем, выбрал ли пользователь артикль (если он необходим)
        val isArticleCorrect = correctArticle.isBlank() || correctArticle == selectedArticle
        // Возвращаем true только если и артикль, и перевод правильные
        return isArticleCorrect && word.Translation == selectedTranslation
    }

//    private fun updateAnswerUI(isCorrect: Boolean) {
//        binding.apply {
//            resultTextView.text = if (isCorrect) getString(R.string.correct) else getString(R.string.incorrect, currentWord?.Artikel, currentWord?.Word, currentWord?.Translation)
//            resultTextView.setTextColor(ContextCompat.getColor(this@MainActivity, if (isCorrect) R.color.correct_answer else R.color.wrong_answer))
//            currentWordTextView.setTextColor(ContextCompat.getColor(this@MainActivity, if (isCorrect) R.color.correct_answer else R.color.wrong_answer))
//        }
//        updateProgressBar()
//    }

    private fun updateAnswerUI(isCorrect: Boolean) {
        binding.apply {
            resultTextView.text = if (isCorrect) getString(R.string.correct) else getString(R.string.incorrect, currentWord?.Artikel, currentWord?.Word, currentWord?.Translation)
            resultTextView.setTextColor(ContextCompat.getColor(this@MainActivity, if (isCorrect) R.color.correct_answer else R.color.wrong_answer))
            currentWordTextView.setTextColor(ContextCompat.getColor(this@MainActivity, if (isCorrect) R.color.correct_answer else R.color.wrong_answer))
        }
        updateProgressBar()
    }

    private fun updateProgressBar() {
        binding.progressBar.progress = completedWordsCount
    }
    
    private fun prepareForNextWord() {
        binding.apply {
            // Скрываем и сбрасываем элементы UI
            resultTextView.visibility = View.VISIBLE
            currentWordTextView.visibility = View.GONE
            articleRadioGroup.visibility = View.GONE
            translationOption1.visibility = View.GONE
            translationOption2.visibility = View.GONE
            translationOption3.visibility = View.GONE
            translationOption4.visibility = View.GONE
            buttonNext.visibility = View.VISIBLE // Показываем кнопку "Далее"
        }

        // Обработчик нажатия на кнопку "Далее"
        binding.buttonNext.setOnClickListener {
            currentWord = null // Сброс текущего слова перед выбором нового
            setRandomWord(selectedLevel)
            binding.apply {
                // Показываем элементы UI для нового слова
                currentWordTextView.visibility = View.VISIBLE
                articleRadioGroup.visibility = View.VISIBLE
                translationOption1.visibility = View.VISIBLE
                translationOption2.visibility = View.VISIBLE
                translationOption3.visibility = View.VISIBLE
                translationOption4.visibility = View.VISIBLE
                resultTextView.visibility = View.GONE
                buttonNext.visibility = View.GONE // Скрываем кнопку "Далее"
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        intent.getStringExtra("SelectedLevel")?.let { newLevel ->
            selectedLevel = newLevel
            loadWordsForLevel(newLevel)
        }
    }

    private fun saveLevelProgress(level: String, progress: Int) {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putInt("progress_$level", progress)
            apply()
        }
        Log.d("MainActivity", "Прогресс сохранен для уровня $level: $progress")
    }

    private fun loadLevelProgress(level: String): Int {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return sharedPrefs.getInt("progress_$level", 0)
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
        finish()
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

    private fun updateProgress() {
        lifecycleScope.launch {
            completedWordsCount = dbHelper.getCompletedWordsCount(selectedLevel)
            totalWordsCount = dbHelper.getTotalWordsCount(selectedLevel)
            updateProgressUI()
        }
    }

    private fun updateProgressUI() {
        updateProgress()
        binding.progressBar.max = totalWordsCount
        binding.progressBar.progress = completedWordsCount
        binding.progressText.text = "$completedWordsCount из $totalWordsCount слов"
        //Log.d("MainActivity", "Прогресс обновлен: для уровня $selectedLevel - $completedWordsCount из $totalWordsCount слов.")
    }

    override fun onPause() {
        super.onPause()
        saveCurrentState() // Сохраняем состояние при приостановке активности
        Log.d("MainActivity", "Состояние сохранено: для уровня $selectedLevel - $completedWordsCount из $totalWordsCount слов.")
    }

    private fun isFirstLaunch(): Boolean {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return sharedPrefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    override fun onResume() {
        super.onResume()
        // Если выбран новый уровень или это первый запуск, обновляем прогресс
        if (intent.hasExtra("SelectedLevel")) {
            selectedLevel = intent.getStringExtra("SelectedLevel") ?: "A1"
            loadWordsForLevel(selectedLevel)
        } else if (!stateRestored) {
            restoreStateIfNeeded()
            stateRestored = true
        } else {
            updateProgress()
        }
    }

    override fun onDestroy() {
        if (::runnable.isInitialized) {
            handler.removeCallbacks(runnable)
        }
        super.onDestroy()
    }
}
