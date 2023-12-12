import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.ddd.databinding.LevelItemBinding

data class Level(val name: String, val progress: Int)

class LevelsAdapter( private val levels: List<Level>, private val onLevelSelected: (Level) -> Unit) : RecyclerView.Adapter<LevelsAdapter.LevelViewHolder>()
{

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
            binding.root.setOnClickListener { // Установка обработчика нажатия
                onLevelSelected(level) // Вызов лямбда-функции с текущим уровнем
            }
        }
    }
}
