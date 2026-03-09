package com.samyak.repostore.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.samyak.repostore.R
import com.samyak.repostore.data.model.AppItem
import com.samyak.repostore.data.model.AppTag
import com.samyak.repostore.databinding.ItemAppCardBinding
import java.text.NumberFormat
import java.util.Locale

class AppListAdapter(
    private val onItemClick: (AppItem) -> Unit
) : ListAdapter<AppItem, AppListAdapter.AppViewHolder>(AppDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class AppViewHolder(
        private val binding: ItemAppCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }
        
        fun bind(item: AppItem) {
            val repo = item.repo
            
            binding.apply {
                tvAppName.text = repo.name
                tvDeveloper.text = repo.owner.login
                tvDescription.text = repo.description ?: "No description available"
                tvStars.text = formatNumber(repo.stars)
                tvLanguage.text = repo.language ?: "Unknown"
                
                // Version from release
                if (item.latestRelease != null) {
                    tvVersion.text = item.latestRelease.tagName
                    tvVersion.visibility = View.VISIBLE
                } else {
                    tvVersion.visibility = View.GONE
                }
                
                // Tag badge
                when (item.tag) {
                    AppTag.NEW -> {
                        chipTag.visibility = View.VISIBLE
                        chipTag.text = itemView.context.getString(R.string.tag_new)
                        chipTag.setChipBackgroundColorResource(R.color.tag_new)
                    }
                    AppTag.UPDATED -> {
                        chipTag.visibility = View.VISIBLE
                        chipTag.text = itemView.context.getString(R.string.tag_updated)
                        chipTag.setChipBackgroundColorResource(R.color.tag_updated)
                    }
                    AppTag.ARCHIVED -> {
                        chipTag.visibility = View.VISIBLE
                        chipTag.text = itemView.context.getString(R.string.tag_archived)
                        chipTag.setChipBackgroundColorResource(R.color.tag_archived)
                    }
                    null -> chipTag.visibility = View.GONE
                }
                
                // Load avatar
                Glide.with(ivAppIcon)
                    .load(repo.owner.avatarUrl)
                    .placeholder(R.drawable.ic_app_placeholder)
                    .circleCrop()
                    .into(ivAppIcon)
            }
        }
        
        private fun formatNumber(number: Int): String {
            return when {
                number >= 1_000_000 -> String.format(Locale.US, "%.1fM", number / 1_000_000.0)
                number >= 1_000 -> String.format(Locale.US, "%.1fK", number / 1_000.0)
                else -> NumberFormat.getInstance(Locale.US).format(number)
            }
        }
    }
    
    class AppDiffCallback : DiffUtil.ItemCallback<AppItem>() {
        override fun areItemsTheSame(oldItem: AppItem, newItem: AppItem): Boolean {
            return oldItem.repo.id == newItem.repo.id
        }
        
        override fun areContentsTheSame(oldItem: AppItem, newItem: AppItem): Boolean {
            return oldItem == newItem
        }
    }
}
