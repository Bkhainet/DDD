package com.example.ddd

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.ddd.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private var completedWordsCountError = 0
    private var initialErrorCount = 0
    private var totalWordsCount = 0
    private var completedWordsCount = 0
    private var selectedLevel = "A1"
    private var currentWord: WordEntry? = null
    private lateinit var dbHelper: DatabaseHelper
    private var stateRestored = false
    private var isDbInitialized = false
    private var isWordLoading = false
    private var isInErrorCorrectionMode = false
    private var shouldRestoreState = true

    companion object {

        const val PREFS_NAME = "MyAppSettings"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_CURRENT_WORD = "current_word"
        const val KEY_COMPLETED_COUNT = "completed_count"

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate: начало")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)
        val viewModelFactory = MainViewModelFactory(dbHelper)
        viewModel = ViewModelProvider(this, viewModelFactory).get(MainViewModel::class.java)

        viewModel.wordsWithError.observe(this, Observer { words ->
            // Тут можно обновить UI с новым количеством ошибок
            updateErrorProgressUI(words.size)
        })

        isInErrorCorrectionMode = intent.getBooleanExtra("ErrorCorrectionMode", false)

        selectedLevel = intent.getStringExtra("SelectedLevel") ?: "A1" // Получаем уровень, по умолчанию A1

        setupLiveDataObservers()
        checkFirstLaunchAndInitializeDb()
        setupUI()

        binding.buttonNext.setOnClickListener {
            prepareForNextWord()
        }

        if (isInErrorCorrectionMode) {
            Log.d("MainActivity", "onCreate: Режим исправления ошибок активирован")
            handleInErrorCorrectionMode()
            shouldRestoreState = false
        } else {
            Log.d("MainActivity", "onCreate: Нормальный режим")
            if (isFirstLaunch()) {
                initializeDatabase()
            }
        }
        updateErrorProgressVisibility()
        lifecycleScope.launch {
            if (!stateRestored) {
                restoreStateIfNeeded()
            }
        }
    }

    private fun updateErrorProgressUI(errorCount: Int) {
        binding.errorProgressText.text = "Ошибок: $errorCount"
    }

    private fun initializeDatabase() {
        lifecycleScope.launch {
            Log.d("MainActivity", "initializeDatabase: Инициализация базы данных")
            dbHelper.initializeDatabase()
            isDbInitialized = true
            updateProgress()
        }
    }

    private fun setupLiveDataObservers() {
        viewModel.currentWord.observe(this) { word ->
            word?.let {
                currentWord = it
                updateUIWithCurrentWord(it)
                Log.d("MainActivity", "Текущее слово обновлено: ${it.Word}")
                lifecycleScope.launch {
                    viewModel.getTranslationOptions(it, selectedLevel)
                }

            }
        }
        viewModel.translationOptions.observe(this) { options ->
            showTranslationOptions(options)
        }
    }

    private fun handleInErrorCorrectionMode() {
        Log.d("MainActivity", "handleInErrorCorrectionMode: начало")
        lifecycleScope.launch {
            val errorCount = dbHelper.getErrorWordsCount()
            Log.d("MainActivity", "Количество ошибок: $errorCount")

            if (errorCount == 0) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Вы исправили все ошибки!", Toast.LENGTH_LONG).show()
                    navigateToLevelSelection()
                }
            } else {
                viewModel.getRandomWordWithError()
            }
        }
        Log.d("MainActivity", "handleInErrorCorrectionMode: конец")
    }

    private fun navigateToLevelSelection() {
        Log.d("MainActivity", "navigateToLevelSelection: Переход к выбору уровня")
        val intent = Intent(this, LevelSelectionActivity::class.java)
        startActivity(intent)
        finish()
    }

    private suspend fun restoreStateIfNeeded() {
        if (!shouldRestoreState) return
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedWordText = sharedPrefs.getString("${KEY_CURRENT_WORD}_$selectedLevel", null)
        completedWordsCount = sharedPrefs.getInt("${KEY_COMPLETED_COUNT}_$selectedLevel", 0)

        if (savedWordText != null) {
            viewModel.loadWordByWordText(savedWordText)
            Log.d("MainActivity", "restoreStateIfNeeded: Восстановлено сохраненное слово: $savedWordText")
        } else {
            if (!isInErrorCorrectionMode) {
                setRandomWord(selectedLevel)
                //viewModel.getRandomWord(selectedLevel)
            }
            if (isInErrorCorrectionMode) {
                viewModel.getRandomWordWithError()
            } else {
                //
            }
            stateRestored = true // Установка флага после восстановления состояния
        }
        updateLevelAndProgressUI(selectedLevel, completedWordsCount)
    }

    private fun updateLevelAndProgressUI(level: String, progress: Int) {
        binding.progressBar.progress = progress
    }

    private fun checkFirstLaunchAndInitializeDb() {
        if (isFirstLaunch()) {
            lifecycleScope.launch {
                dbHelper.initializeDatabase()
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean(KEY_FIRST_LAUNCH, false)
                    .apply()
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
                button.setOnClickListener {
                    currentWord?.let { word ->
                        checkTranslation(it as Button, word)
                    }
                }
            }
        }
    }

    private suspend fun setRandomWord(level: String) {
        Log.d("MainActivity", "setRandomWord: Запрос на загрузку слова для уровня $level")
        if (!isWordLoading) {
            isWordLoading = true
            viewModel.getRandomWord(level)
            isWordLoading = false // Сбросить флаг после загрузки слова
            Log.d("MainActivity", "setRandomWord: Слово загружено")
        } else {
            Log.d("MainActivity", if (currentWord != null) "setRandomWord: Текущее слово уже установлено." else "setRandomWord: Загрузка слова уже идёт."
            )
        }
    }

    private suspend fun setRandomWordWithError() {
        if (!isWordLoading) {
            isWordLoading = true
            viewModel.getRandomWordWithError()
            isWordLoading = false // Сбросить флаг после загрузки слова
        } else {
            Log.d(
                "MainActivity",
                if (currentWord != null) "setRandomWordWithError: Текущее слово уже установлено." else "setRandomWordWithError: Загрузка слова уже идёт."
            )
        }
    }

    private fun updateUIWithCurrentWord(word: WordEntry) {
        Log.d("MainActivity", "updateUIWithCurrentWord: Обновление UI с новым словом ${word.Word}")
        runOnUiThread {
            binding.apply {
                currentWordTextView.text = word.Word
                articleRadioGroup.clearCheck()
                currentWordTextView.setTextColor(
                    ContextCompat.getColor(
                        this@MainActivity,
                        R.color.default_text_color
                    )
                )

                val isArticleNeeded = word.Artikel?.isNotBlank() ?: false
                derRadioButton.visibility = if (isArticleNeeded) View.VISIBLE else View.GONE
                dieRadioButton.visibility = if (isArticleNeeded) View.VISIBLE else View.GONE
                dasRadioButton.visibility = if (isArticleNeeded) View.VISIBLE else View.GONE

            }
        }
    }

    private fun showTranslationOptions(translations: List<String>) {
        Log.d("MainActivity", "Предлагаемые варианты перевода: $translations")

        binding.apply {
            val buttons = listOf(
                translationOption1,
                translationOption2,
                translationOption3,
                translationOption4
            )
            buttons.zip(translations).forEachIndexed { index, (button, translation) ->
                button.text = translation
                Log.d("MainActivity", "Кнопка перевода ${index + 1}: $translation")

            }
        }
    }

    private fun checkTranslation(clickedButton: Button, word: WordEntry) {
        Log.d("MainActivity", "checkTranslation: Проверка перевода для слова ${word.Word}")
        Log.d("MainActivity", "Нажатие на кнопку перевода: ${clickedButton.text}")

        // Получение выбранного артикля
        val selectedArticle = when (binding.articleRadioGroup.checkedRadioButtonId) {
            R.id.derRadioButton -> "der"
            R.id.dieRadioButton -> "die"
            R.id.dasRadioButton -> "das"
            else -> ""
        }

        // Получение выбранного пользователем перевода
        val userTranslation = clickedButton.text.toString()

        // Проверка правильности ответа
        val isCorrect = checkAnswer(selectedArticle, userTranslation, word)
        Log.d("MainActivity", "checkTranslation: Выбран перевод '$userTranslation' для слова '${word.Word}'. Правильно? $isCorrect")

        // Обновление UI в зависимости от правильности ответа
        updateAnswerUI(isCorrect)

        // Обновление флага ошибки в базе данных
        lifecycleScope.launch {
            val errorFlag = if (isCorrect) 0 else 1
            dbHelper.updateErrorFlag(word.Word, errorFlag)
            dbHelper.updateWordsWithError()

            viewModel.refreshWordsWithError() // Это обновит список слов с ошибками
            viewModel.updateErrorWordsCount() // Это обновит счетчик ошибок
        }

        // Дополнительная логика для обработки ответа
        if (isCorrect) {
            onCorrectAnswer()
        } else {
            prepareForNextWord()
            // Логика для неправильного ответа
            if (!isInErrorCorrectionMode) {
                lifecycleScope.launch {
                    dbHelper.updateErrorFlag(word.Word, 1) // Устанавливаем ErrorFlag для неправильных ответов
                    updateErrorProgress()
                    dbHelper.updateWordsWithError()                }
            }
        }

        prepareForNextWord()
    }

    private fun onCorrectAnswer() {
        lifecycleScope.launch {
            viewModel.refreshWordsWithError() // Обновляем список слов с ошибками
            completedWordsCount++
            Log.d("MainActivity", "onCorrectAnswer: Обновление прогресса - $completedWordsCount из $totalWordsCount")
            saveLevelProgress(selectedLevel, completedWordsCount)
            updateProgressUI()

            if (isInErrorCorrectionMode) {
                val errorCount = dbHelper.getErrorWordsCount()
                if (errorCount == 0) {
                    isInErrorCorrectionMode = false
                    navigateToLevelSelection()
                } else {
                    // Если остались ошибки, загрузите следующее слово с ошибкой
                    viewModel.getRandomWordWithError()
                }
            }
        }
    }

    private fun checkAnswer(
        selectedArticle: String,
        selectedTranslation: String,
        word: WordEntry
    ): Boolean {
        // Проверяем, имеется ли артикль у слова
        val correctArticle = word.Artikel ?: ""
        // Проверяем, выбрал ли пользователь артикль (если он необходим)
        val isArticleCorrect = correctArticle.isBlank() || correctArticle == selectedArticle
        // Возвращаем true только если и артикль, и перевод правильные
        return isArticleCorrect && word.Translation == selectedTranslation
    }

    private fun updateAnswerUI(isCorrect: Boolean) {
        binding.apply {
            resultTextView.text = if (isCorrect) getString(R.string.correct) else getString(
                R.string.incorrect,
                currentWord?.Artikel,
                currentWord?.Word,
                currentWord?.Translation
            )
            resultTextView.setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (isCorrect) R.color.correct_answer else R.color.wrong_answer
                )
            )
            currentWordTextView.setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (isCorrect) R.color.correct_answer else R.color.wrong_answer
                )
            )
        }
        updateProgressBar()
    }

    private fun updateProgressBar() {
        binding.progressBar.progress = completedWordsCount
    }

    private fun prepareForNextWord() {
        isWordLoading = false
        currentWord = null
        Log.d("MainActivity", "Подготовка к следующему слову")
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
            lifecycleScope.launch {
                if (!isWordLoading) {
                    //isWordLoading = true
                    if (isInErrorCorrectionMode) {
                        setRandomWordWithError()
                    } else {
                        setRandomWord(selectedLevel)
                    }
                }
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
        Log.d("MainActivity", "Подготовка к загрузке следующего слова")
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
            putInt("${KEY_COMPLETED_COUNT}_$level", progress)
            apply()
        }
    }

    private fun loadLevelProgress(level: String): Int {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return sharedPrefs.getInt("${KEY_COMPLETED_COUNT}_$level", 0)
    }

    private fun loadWordsForLevel(level: String) {
        lifecycleScope.launch {
            completedWordsCount = loadLevelProgress(level)
            totalWordsCount = dbHelper.getCountOfLevelWords(level)
            updateProgressUI()

            setRandomWord(level)
        }
    }

    // Метод для сохранения текущего состояния в SharedPreferences.
    private fun saveCurrentState() {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putString("${KEY_CURRENT_WORD}_$selectedLevel", currentWord?.Word)
            putInt("${KEY_COMPLETED_COUNT}_$selectedLevel", completedWordsCount)
            apply()
        }
        Log.d("MainActivity", "saveCurrentState: Сохранено текущее слово: ${currentWord?.Word}")
    }

    private fun updateErrorProgressVisibility() {
        if (isInErrorCorrectionMode) {
            binding.progressBar.visibility = View.GONE // Скрыть обычный прогресс
            binding.progressText.visibility = View.GONE
            binding.errorProgressText.visibility = View.VISIBLE // Показать счетчик ошибок
            updateErrorProgress() // Обновить текст счетчика ошибок
        } else {
            binding.progressBar.visibility = View.VISIBLE // Показать обычный прогресс
            binding.progressText.visibility = View.VISIBLE
            binding.errorProgressText.visibility = View.GONE // Скрыть счетчик ошибок
            updateProgress()
        }
    }

    private fun updateErrorProgress() {
        // Получить количество ошибок и обновить TextView
        lifecycleScope.launch {
            completedWordsCountError = dbHelper.getErrorWordsCount()
            updateErrorProgressUI()
        }
    }

    private fun updateProgress() {
        lifecycleScope.launch {
            completedWordsCount = dbHelper.getCompletedWordsCount(selectedLevel)
            totalWordsCount = dbHelper.getTotalWordsCount(selectedLevel)
            updateProgressUI()
            ///
            viewModel.updateProgress(completedWordsCount) // Обновляем LiveData в ViewModel
            // Обновление UI с новым прогрессом
            binding.progressBar.max = totalWordsCount
            binding.progressBar.progress = completedWordsCount
            binding.progressText.text = "$completedWordsCount из $totalWordsCount слов"

        }
    }

    private fun updateProgressUI() {
        updateProgress()
        binding.progressBar.max = totalWordsCount
        binding.progressBar.progress = completedWordsCount
        binding.progressText.text = "$completedWordsCount из $totalWordsCount слов"
    }

    private fun updateErrorProgressUI() {
        lifecycleScope.launch {
            val errorWordsCount = dbHelper.getErrorWordsCount()
            binding.errorProgressText.text = "$errorWordsCount ошибок"
            Log.d("MainActivity", "Прогресс ошибок обновлен: $errorWordsCount ошибок.")
        }
    }

    override fun onPause() {
        super.onPause()
        saveCurrentState() // Сохраняем состояние при приостановке активности
        Log.d("MainActivity", "onPause: Состояние сохранено: для уровня $selectedLevel - $completedWordsCount из $totalWordsCount слов, текущее слово: ${currentWord?.Word}")
    }

    private fun isFirstLaunch(): Boolean {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return sharedPrefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume: Активность возобновлена")

        // Обработка интентов и восстановление состояния, если необходимо
        lifecycleScope.launch {
            if (!stateRestored && shouldRestoreState) {
                lifecycleScope.launch {
                    Log.d("MainActivity", "onResume: Восстановление состояния")
                    restoreStateIfNeeded()
                }
            }
        }
    }

    override fun onBackPressed() {
        navigateToLevelSelection()
    }
}
