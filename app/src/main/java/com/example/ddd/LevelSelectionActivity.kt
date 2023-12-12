package com.example.ddd

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ddd.databinding.ActivityLevelSelectionBinding
import com.example.ddd.databinding.LevelItemBinding

class LevelSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLevelSelectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLevelSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val levels = listOf(
            Level("A1", 30),
            Level("A2", 50),
            Level("B1", 70)
        )

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
}

data class Level(val name: String, val progress: Int)

class LevelsAdapter(
    private val levels: List<Level>,
    private val onLevelSelected: (Level) -> Unit
) : RecyclerView.Adapter<LevelsAdapter.LevelViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LevelViewHolder {
        val binding = LevelItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LevelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LevelViewHolder, position: Int) {
        val level = levels[position]
        holder.bind(level, onLevelSelected)
    }

    override fun getItemCount(): Int = levels.size

    class LevelViewHolder(private val binding: LevelItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(level: Level, onLevelSelected: (Level) -> Unit) {
            binding.levelName.text = level.name
            binding.levelProgress.progress = level.progress
            itemView.setOnClickListener { onLevelSelected(level) }
        }
    }
}