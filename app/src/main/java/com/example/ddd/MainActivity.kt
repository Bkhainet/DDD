package com.example.ddd

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dbHelper = DatabaseHelper(this)
        val viewModelFactory = MainViewModelFactory(dbHelper)
        viewModel = ViewModelProvider(this, viewModelFactory).get(MainViewModel::class.java)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        setupUI()
        handleIntent(intent) // Обработка Intent
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
        lifecycleScope.launch {
            viewModel.getRandomWord(level)?.let { word ->
                currentWord = word // Установка текущего слова
                updateUIWithCurrentWord(word)
            }
        }
    }

    private fun updateUIWithCurrentWord(word: WordEntry) {
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

        if (checkAnswer(selectedArticle, userTranslation, word)) {
            updateAnswerUI(true)
        } else {
            updateAnswerUI(false)
        }
        prepareForNextWord()
    }

    private fun updateAnswerUI(isCorrect: Boolean) {
        binding.apply {
            resultTextView.text = if (isCorrect) getString(R.string.correct) else getString(R.string.incorrect, currentWord?.Artikel, currentWord?.Word, currentWord?.Translation)
            resultTextView.setTextColor(ContextCompat.getColor(this@MainActivity, if (isCorrect) R.color.correct_answer else R.color.wrong_answer))
            currentWordTextView.setTextColor(ContextCompat.getColor(this@MainActivity, if (isCorrect) R.color.correct_answer else R.color.wrong_answer))
        }
        completedWordsCount++
        updateProgressBar()
    }

    private fun checkAnswer(selectedArticle: String, selectedTranslation: String, word: WordEntry): Boolean {
        return selectedArticle == word.Artikel && selectedTranslation == word.Translation
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

    private fun loadWordsForLevel(level: String) {
        lifecycleScope.launch {
            totalWordsCount = viewModel.getCountOfLevelWords(level)
            binding.progressBar.max = totalWordsCount
            updateProgressBar()
            setRandomWord(level)
        }
    }

//    override fun onPause() {
//        super.onPause()
//        // Никаких действий не выполняется при приостановке
//    }

    override fun onDestroy() {
        if (::runnable.isInitialized) {
            handler.removeCallbacks(runnable)
        }
        super.onDestroy()
    }
}
