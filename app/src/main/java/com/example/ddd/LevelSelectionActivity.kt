package com.example.ddd

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ddd.databinding.ActivityLevelSelectionBinding
import com.example.ddd.databinding.LevelItemBinding
import kotlinx.coroutines.launch

class LevelSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLevelSelectionBinding
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLevelSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Инициализация DatabaseHelper и MainViewModelFactory
        val dbHelper = DatabaseHelper(this)
        val viewModelFactory = MainViewModelFactory(dbHelper)
        viewModel = ViewModelProvider(this, viewModelFactory).get(MainViewModel::class.java)

        // Наблюдение за изменениями в количестве слов с ошибками и обновление видимости кнопки
        viewModel.errorWordsCount.observe(this, Observer { count ->
            binding.buttonErrorCorrection.visibility = if (count > 0) View.VISIBLE else View.GONE
            //
        })


        // Обновление количества слов с ошибками при создании активности
        viewModel.updateErrorWordsCount()

        lifecycleScope.launch { // Запуск корутины внутри lifecycleScope
            val levels = loadLevels()
            binding.levelsRecyclerView.apply {
                layoutManager = LinearLayoutManager(this@LevelSelectionActivity)
                adapter = LevelsAdapter(levels) { selectedLevel ->
                    val intent = Intent(this@LevelSelectionActivity, MainActivity::class.java)
                    intent.putExtra("SelectedLevel", selectedLevel.name)
                    startActivity(intent)
                    finish()
                }
            }
        }

        binding.buttonErrorCorrection.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("ErrorCorrectionMode", true)
            startActivity(intent)
        }
    }

    private suspend fun loadLevels(): List<Level> {
        val sharedPrefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val dbHelper = DatabaseHelper(this)

        // Асинхронно получаем общее количество слов для каждого уровня
        val totalWordsA1 = dbHelper.getTotalWordsCount("A1")
        val totalWordsA2 = dbHelper.getTotalWordsCount("A2")
        val totalWordsB1 = dbHelper.getTotalWordsCount("B1")

        return listOf(
            Level("A1", sharedPrefs.getInt("${MainActivity.KEY_COMPLETED_COUNT}_A1", 0), totalWordsA1),
            Level("A2", sharedPrefs.getInt("${MainActivity.KEY_COMPLETED_COUNT}_A2", 0), totalWordsA2),
            Level("B1", sharedPrefs.getInt("${MainActivity.KEY_COMPLETED_COUNT}_B1", 0), totalWordsB1)
            // Добавьте другие уровни по аналогии
        )
    }

    data class Level(val name: String, val progress: Int, val total: Int)
    class LevelsAdapter(
        private val levels: List<Level>,
        private val onLevelSelected: (Level) -> Unit
    ) : RecyclerView.Adapter<LevelsAdapter.LevelsViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LevelsViewHolder {
            val binding = LevelItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return LevelsViewHolder(binding)
        }

        override fun onBindViewHolder(holder: LevelsViewHolder, position: Int) {
            val level = levels[position]
            holder.bind(level, onLevelSelected)
        }

        override fun getItemCount(): Int = levels.size

    class LevelsViewHolder(private val binding: LevelItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(level: Level, onLevelSelected: (Level) -> Unit) {
            binding.levelName.text = level.name
            binding.levelProgress.text = "${level.progress} из ${level.total}"

            // Обновляем ProgressBar для каждого уровня
            binding.progressBar.progress = level.progress
            binding.progressBar.max = level.total

            itemView.setOnClickListener { onLevelSelected(level) }
        }
    }

    }
}

