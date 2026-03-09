package com.samyak.repostore.ui.adapter

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.samyak.repostore.R
import com.samyak.repostore.databinding.ItemScreenshotFullscreenBinding

class ScreenshotPagerAdapter(
    private val screenshots: List<String>,
    private val onPhotoTap: (() -> Unit)? = null
) : RecyclerView.Adapter<ScreenshotPagerAdapter.ScreenshotViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScreenshotViewHolder {
        val binding = ItemScreenshotFullscreenBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ScreenshotViewHolder(binding, onPhotoTap)
    }

    override fun onBindViewHolder(holder: ScreenshotViewHolder, position: Int) {
        holder.bind(screenshots[position])
    }

    override fun getItemCount(): Int = screenshots.size

    class ScreenshotViewHolder(
        private val binding: ItemScreenshotFullscreenBinding,
        private val onPhotoTap: (() -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(imageUrl: String) {
            binding.progressBar.visibility = View.VISIBLE

            // Set tap listener for toggling UI
            binding.ivScreenshot.setOnClickListener {
                onPhotoTap?.invoke()
            }

            Glide.with(binding.ivScreenshot.context)
                .load(imageUrl)
                .placeholder(R.drawable.bg_screenshot_placeholder)
                .error(R.drawable.bg_screenshot_placeholder)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        binding.progressBar.visibility = View.GONE
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        binding.progressBar.visibility = View.GONE
                        return false
                    }
                })
                .into(binding.ivScreenshot)
        }
    }
}
