package com.samyak.repostore.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.samyak.repostore.R
import com.samyak.repostore.databinding.ItemScreenshotBinding

class ScreenshotAdapter(
    private val onScreenshotClick: (String, Int) -> Unit
) : ListAdapter<String, ScreenshotAdapter.ScreenshotViewHolder>(ScreenshotDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScreenshotViewHolder {
        val binding = ItemScreenshotBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ScreenshotViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ScreenshotViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class ScreenshotViewHolder(
        private val binding: ItemScreenshotBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(imageUrl: String, position: Int) {
            Glide.with(binding.ivScreenshot)
                .load(imageUrl)
                .placeholder(R.drawable.bg_screenshot_placeholder)
                .error(R.drawable.bg_screenshot_placeholder)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(binding.ivScreenshot)

            val clickListener = { _: android.view.View ->
                onScreenshotClick(imageUrl, position)
            }
            
            binding.root.setOnClickListener(clickListener)
            binding.ivScreenshot.setOnClickListener(clickListener)
        }
    }

    class ScreenshotDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
    }
}
