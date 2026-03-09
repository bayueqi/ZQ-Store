package com.samyak.repostore.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.samyak.repostore.R
import com.samyak.repostore.data.model.AppCategory
import com.samyak.repostore.databinding.ItemCategoryChipBinding

class CategoryAdapter(
    private val onCategorySelected: (AppCategory) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {
    
    private val categories = AppCategory.entries.toList()
    private var selectedCategory = AppCategory.ALL
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryChipBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }
    
    override fun getItemCount() = categories.size
    
    fun setSelectedCategory(category: AppCategory) {
        val oldPosition = categories.indexOf(selectedCategory)
        val newPosition = categories.indexOf(category)
        selectedCategory = category
        notifyItemChanged(oldPosition)
        notifyItemChanged(newPosition)
    }
    
    inner class CategoryViewHolder(
        private val binding: ItemCategoryChipBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            binding.chipCategory.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onCategorySelected(categories[position])
                }
            }
        }
        
        fun bind(category: AppCategory) {
            binding.chipCategory.apply {
                text = category.displayName
                isChecked = category == selectedCategory
            }
        }
    }
}
