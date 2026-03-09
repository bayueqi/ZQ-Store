package com.samyak.repostore.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.samyak.repostore.R
import com.samyak.repostore.data.model.AppItem
import com.samyak.repostore.databinding.ItemAppPlaystoreBinding
import java.util.Locale

class PlayStoreAppAdapter(
    private val onItemClick: (AppItem) -> Unit
) : ListAdapter<AppItem, PlayStoreAppAdapter.AppViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppPlaystoreBinding.inflate(
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
        private val binding: ItemAppPlaystoreBinding
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
                // Show actual GitHub stars count
                tvRating.text = formatNumber(repo.stars)

                Glide.with(ivAppIcon)
                    .load(repo.owner.avatarUrl)
                    .placeholder(R.drawable.ic_app_placeholder)
                    .into(ivAppIcon)
            }
        }

        private fun formatNumber(number: Int): String {
            return when {
                number >= 1_000_000 -> String.format(Locale.US, "%.1fM", number / 1_000_000.0)
                number >= 1_000 -> String.format(Locale.US, "%.1fK", number / 1_000.0)
                else -> number.toString()
            }
        }
    }

    class AppDiffCallback : DiffUtil.ItemCallback<AppItem>() {
        override fun areItemsTheSame(oldItem: AppItem, newItem: AppItem) =
            oldItem.repo.id == newItem.repo.id

        override fun areContentsTheSame(oldItem: AppItem, newItem: AppItem) =
            oldItem == newItem
    }
}
